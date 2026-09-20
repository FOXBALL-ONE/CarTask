package top.foxball.cartask.task

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import top.foxball.cartask.audit.AuditRequestContext
import top.foxball.cartask.entity.AccessRecord
import top.foxball.cartask.entity.StoredFile
import top.foxball.cartask.entity.SyncCheckpoint
import top.foxball.cartask.entity.SyncTaskRun
import top.foxball.cartask.handler.VehicleAccessRecordSyncInProgressException
import top.foxball.cartask.keytop.KeytopProperties
import top.foxball.cartask.keytop.KeytopService
import top.foxball.cartask.keytop.KeytopSyncRateLimiter
import top.foxball.cartask.repository.AccessRecordRepository
import top.foxball.cartask.repository.ParkingPlateRepository
import top.foxball.cartask.service.FileService
import top.foxball.cartask.service.SyncCheckpointService
import top.foxball.cartask.service.SyncTaskHistoryService
import top.foxball.cartask.service.SyncTaskProgressService
import top.foxball.cartask.service.SyncTaskRunCommand
import top.foxball.cartask.shared.PlateNumbers
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.ceil

data class CarCapInfoSyncResult(
    val processedCount: Int,
    val localPhotoCount: Int,
    val failedPhotoCount: Int,
    val startTime: LocalDateTime,
    val cursorTime: LocalDateTime,
)

data class CarCapInfoSyncPreview(
    val initialSync: Boolean,
    val pendingCount: Int?,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val checkpointTime: LocalDateTime?,
)


@Component
class SynCarCapInfoTask(
    private val keytopService: KeytopService,
    private val accessRecordRepository: AccessRecordRepository,
    private val objectMapper: ObjectMapper,
    private val keytopProperties: KeytopProperties,
    private val fileService: FileService,
    private val syncCheckpointService: SyncCheckpointService,
    private val syncTaskHistoryService: SyncTaskHistoryService,
    private val syncTaskProgressService: SyncTaskProgressService = SyncTaskProgressService(),
    private val parkingPlateRepository: ParkingPlateRepository,
    private val keytopSyncRateLimiter: KeytopSyncRateLimiter? = null,
) {
    
    
    // 注意：这三个入口不能包 @Transactional——内部会分页调用科拓接口、逐条下载抓拍图片，
    // 都是慢速网络 IO；如果外层持有一个数据库事务，这条连接会被同步过程占住几十秒到几分钟，
    // 在 HikariCP 连接数有限的情况下会把登录等其它请求一起饿死。每条记录的 save() 本身就是
    // 独立提交的小事务，且调用方本就接受"处理到哪算哪、不整体回滚"（历史上这里标的是
    // noRollbackFor），拆开完全等价。
    fun synCarCapInfoList() {
        AuditRequestContext.withRun {
            try {
                executeIncremental(SyncTaskRun.Trigger.SCHEDULED)
            } catch (exception: VehicleAccessRecordSyncInProgressException) {
                logger.warn("车辆进出记录同步仍在执行，本次定时任务跳过")
            } catch (exception: RuntimeException) {
                logger.error("车辆进出记录同步失败", exception)
            }
        }
    }


    fun synchronize(): CarCapInfoSyncResult = executeIncremental(SyncTaskRun.Trigger.MANUAL)


    fun reconcileCarCapInfoList() {
        AuditRequestContext.withRun {
            try {
                executeReconciliation(SyncTaskRun.Trigger.SCHEDULED)
            } catch (exception: VehicleAccessRecordSyncInProgressException) {
                logger.warn("车辆进出记录补偿同步仍在执行，本次定时任务跳过")
            } catch (exception: RuntimeException) {
                logger.error("车辆进出记录补偿同步失败", exception)
            }
        }
    }
    
    
    private fun executeIncremental(trigger: SyncTaskRun.Trigger): CarCapInfoSyncResult = execute(
        trigger = trigger,
        taskKey = TASK_KEY,
        taskName = TASK_NAME,
        synchronization = ::synchronizeIncrementally,
    )
    
    
    private fun executeReconciliation(trigger: SyncTaskRun.Trigger): CarCapInfoSyncResult = execute(
        trigger = trigger,
        taskKey = RECONCILIATION_TASK_KEY,
        taskName = RECONCILIATION_TASK_NAME,
        synchronization = ::synchronizeReconciliation,
    )
    
    
    private fun execute(
        trigger: SyncTaskRun.Trigger,
        taskKey: String,
        taskName: String,
        synchronization: () -> CarCapInfoSyncResult,
    ): CarCapInfoSyncResult {
        val startedAt = LocalDateTime.now()
        syncTaskProgressService.start(taskKey, taskName, startedAt)
        try {
            val snapshot = keytopSyncRateLimiter?.snapshot()
            val result = if (snapshot == null) synchronization()
            else keytopSyncRateLimiter.withSnapshot(snapshot, synchronization)
            recordHistory(taskKey, taskName, trigger, SyncTaskRun.Status.SUCCESS, startedAt, result, null)
            return result
        } catch (exception: VehicleAccessRecordSyncInProgressException) {
            throw exception
        } catch (exception: RuntimeException) {
            recordHistory(taskKey, taskName, trigger, SyncTaskRun.Status.FAILED, startedAt, null, exception)
            throw exception
        } finally {
            syncTaskProgressService.finish(taskKey, startedAt)
        }
    }
    
    
    // 同样不能包事务：内部会调科拓接口，理由见 synCarCapInfoList 上的注释。
    fun previewSynchronization(): CarCapInfoSyncPreview {
        val snapshot = keytopSyncRateLimiter?.snapshot()
        if (!executionLock.tryLock()) {
            throw VehicleAccessRecordSyncInProgressException()
        }
        try {
            validateSynchronizationConfiguration()
            val syncEndTime = LocalDateTime.now()
            val checkpoint = syncCheckpointService.find(SYNC_KEY)
            val checkpointTime = checkpoint?.cursorTime
            val startTime = incrementalStartTime(checkpointTime, syncEndTime)
            val response = if (snapshot == null) {
                keytopService.getCarInoutInfo(
                    pageIndex = 1,
                    pageSize = 1,
                    startTime = startTime,
                    endTime = syncEndTime,
                )
            } else {
                keytopSyncRateLimiter.withSnapshot(snapshot) {
                    keytopService.getCarInoutInfo(
                        pageIndex = 1,
                        pageSize = 1,
                        startTime = startTime,
                        endTime = syncEndTime,
                    )
                }
            }
            require(response.code == 0) {
                "Keytop 车辆进出接口预检失败：${response.code ?: "未知"} ${response.message.orEmpty()}".trim()
            }
            val data = parseData(response.data)
            return CarCapInfoSyncPreview(
                initialSync = checkpointTime == null,
                pendingCount = data.totalCount,
                startTime = startTime,
                endTime = syncEndTime,
                checkpointTime = checkpointTime,
            )
        } finally {
            executionLock.unlock()
        }
    }
    

    private fun synchronizeIncrementally(): CarCapInfoSyncResult {
        if (!executionLock.tryLock()) {
            throw VehicleAccessRecordSyncInProgressException()
        }
        try {
            validateSynchronizationConfiguration()
            val syncEndTime = LocalDateTime.now()
            val checkpoint = syncCheckpointService.loadOrCreate(SYNC_KEY)
            val startTime = incrementalStartTime(checkpoint.cursorTime, syncEndTime)
            val batchId = UUID.randomUUID().toString()
            checkpoint.status = SyncCheckpoint.Status.RUNNING
            checkpoint.lastBatchId = batchId
            checkpoint.lastError = null
            syncCheckpointService.save(checkpoint)
            val result = synchronizeRange(startTime, syncEndTime, TASK_KEY)
            checkpoint.cursorTime = syncEndTime
            checkpoint.cursorExternalId = null
            checkpoint.status = SyncCheckpoint.Status.SUCCESS
            checkpoint.lastSuccessAt = LocalDateTime.now()
            checkpoint.lastError = null
            syncCheckpointService.save(checkpoint)
            logger.info(
                "车辆进出记录同步完成：处理 {} 条，查询区间：{} 至 {}",
                result.processedCount,
                startTime,
                syncEndTime
            )
            return CarCapInfoSyncResult(
                processedCount = result.processedCount,
                localPhotoCount = result.localPhotoCount,
                failedPhotoCount = result.failedPhotoCount,
                startTime = startTime,
                cursorTime = requireNotNull(checkpoint.cursorTime),
            )
        } catch (exception: RuntimeException) {
            syncCheckpointService.find(SYNC_KEY)?.let { checkpoint ->
                checkpoint.status = SyncCheckpoint.Status.FAILED
                checkpoint.lastError = exception.message?.take(2048) ?: exception.javaClass.simpleName
                syncCheckpointService.save(checkpoint)
            }
            throw exception
        } finally {
            executionLock.unlock()
        }
    }
    
    
    private fun synchronizeReconciliation(): CarCapInfoSyncResult {
        if (!executionLock.tryLock()) {
            throw VehicleAccessRecordSyncInProgressException()
        }
        try {
            validateSynchronizationConfiguration()
            val syncEndTime = LocalDateTime.now()
            val startTime = syncEndTime.minus(keytopProperties.carCapInfoReconciliationWindow)
            if (syncCheckpointService.find(SYNC_KEY) == null) {
                syncCheckpointService.save(SyncCheckpoint().apply { syncKey = SYNC_KEY })
            }
            val result = synchronizeRange(startTime, syncEndTime, RECONCILIATION_TASK_KEY)
            logger.info(
                "车辆进出记录补偿同步完成：处理 {} 条，查询区间：{} 至 {}",
                result.processedCount,
                startTime,
                syncEndTime
            )
            return CarCapInfoSyncResult(
                processedCount = result.processedCount,
                localPhotoCount = result.localPhotoCount,
                failedPhotoCount = result.failedPhotoCount,
                startTime = startTime,
                cursorTime = syncEndTime,
            )
        } finally {
            executionLock.unlock()
        }
    }
    
    
    private fun synchronizeRange(startTime: LocalDateTime, syncEndTime: LocalDateTime, taskKey: String): ProcessResult {
        val rateLimitSnapshot = keytopSyncRateLimiter?.snapshot()
        VehiclePhotoDownloadCoordinator(
            concurrency = rateLimitSnapshot?.photoDownloadConcurrency
                ?: keytopProperties.carCapInfoPhotoDownloadConcurrency,
            startInterval = rateLimitSnapshot?.photoDownloadInterval
                ?: keytopProperties.carCapInfoPhotoDownloadInterval,
            fileService = fileService,
            accessRecordRepository = accessRecordRepository,
        ).use { coordinator ->
            retryFailedPhotos(coordinator)
            val firstPage = keytopService.getCarInoutInfo(
                pageIndex = 1,
                pageSize = keytopProperties.carCapInfoPageSize,
                startTime = startTime,
                endTime = syncEndTime,
            )
            require(firstPage.code == 0) {
                "Keytop 车辆进出接口返回失败：${firstPage.code ?: "未知"} ${firstPage.message.orEmpty()}".trim()
            }
            val firstData = parseData(firstPage.data)
            val totalCount = firstData.totalCount
            val pages = if (totalCount != null) {
                ceil(totalCount.toDouble() / keytopProperties.carCapInfoPageSize).toInt().coerceAtLeast(1)
            } else {
                1
            }
            val seen = mutableMapOf<String, AccessRecord>()
            var result = processRecords(firstData.records, seen, coordinator)
            var synchronizedCount = result.processedCount
            syncTaskProgressService.update(taskKey, synchronizedCount, totalCount)
            var localPhotoCount = result.localPhotoCount
            var failedPhotoCount = result.failedPhotoCount
            var pageIndex = 2
            while (pageIndex <= pages || (totalCount == null && firstData.records.size >= keytopProperties.carCapInfoPageSize)) {
                val response = keytopService.getCarInoutInfo(
                    pageIndex = pageIndex,
                    pageSize = keytopProperties.carCapInfoPageSize,
                    startTime = startTime,
                    endTime = syncEndTime,
                )
                require(response.code == 0) {
                    "Keytop 车辆进出接口第 ${pageIndex} 页返回失败：${response.code ?: "未知"} ${response.message.orEmpty()}".trim()
                }
                val data = parseData(response.data)
                result = processRecords(data.records, seen, coordinator)
                synchronizedCount += result.processedCount
                syncTaskProgressService.update(taskKey, synchronizedCount, totalCount)
                localPhotoCount += result.localPhotoCount
                failedPhotoCount += result.failedPhotoCount
                if (totalCount == null && data.records.size < keytopProperties.carCapInfoPageSize) break
                pageIndex++
            }
            return ProcessResult(synchronizedCount, localPhotoCount, failedPhotoCount)
        }
    }
    
    
    private fun incrementalStartTime(checkpointTime: LocalDateTime?, syncEndTime: LocalDateTime): LocalDateTime =
        checkpointTime?.minus(keytopProperties.carCapInfoOverlapWindow)
            ?: syncEndTime.minusDays(INITIAL_SYNC_DAYS)
    
    
    private fun validateSynchronizationConfiguration() {
        require(keytopProperties.carCapInfoPageSize in 1..1000) {
            "车辆进出记录同步分页大小必须在 1 到 1000 之间"
        }
    }
    
    
    private fun recordHistory(
        taskKey: String,
        taskName: String,
        trigger: SyncTaskRun.Trigger,
        status: SyncTaskRun.Status,
        startedAt: LocalDateTime,
        result: CarCapInfoSyncResult?,
        exception: RuntimeException?,
    ) {
        try {
            syncTaskHistoryService.record(
                SyncTaskRunCommand(
                    taskKey = taskKey,
                    taskName = taskName,
                    trigger = trigger,
                    status = status,
                    startedAt = startedAt,
                    finishedAt = LocalDateTime.now(),
                    dataStartTime = result?.startTime,
                    dataEndTime = result?.cursorTime,
                    processedCount = result?.processedCount,
                    localPhotoCount = result?.localPhotoCount,
                    failedPhotoCount = result?.failedPhotoCount,
                    summary = result?.let {
                        "处理 ${it.processedCount} 条，图片落地 ${it.localPhotoCount} 张，待重试 ${it.failedPhotoCount} 张"
                    },
                    error = exception?.message?.take(2048) ?: exception?.javaClass?.simpleName,
                ),
            )
        } catch (historyException: RuntimeException) {
            logger.warn("写入车辆进出记录同步执行历史失败", historyException)
        }
    }
    
    
    private fun processRecords(
        records: List<JsonNode>,
        seen: MutableMap<String, AccessRecord>,
        coordinator: VehiclePhotoDownloadCoordinator,
    ): ProcessResult {
        var processed = 0
        val photoTasks = mutableListOf<VehiclePhotoDownloadCoordinator.PhotoDownloadTask>()
        val platesByNormalizedNumber = parkingPlateRepository
            .findAll()
            .groupBy { PlateNumbers.normalize(it.plate) }
            .mapValues { (_, plates) -> plates.first() }
        records.forEach { node ->
            val record = parseRecord(node) ?: return@forEach
            val carBrand = firstText(node, "carBrand")?.trim().orEmpty()
            val key = requireNotNull(record.sourceRecordId)
            if (seen.containsKey(key)) return@forEach
            val existing = seen[key]
                ?: accessRecordRepository.findBySourceRecordId(key)
                ?: accessRecordRepository.findByIdentity(record.carNumber, record.inAndOut, record.inAndOutTime)
            val stored = if (existing == null) {
                record.photoSyncStatus = if (record.sourcePhotoUrl?.trim().isNullOrBlank()) {
                    AccessRecord.PhotoSyncStatus.NOT_AVAILABLE
                } else {
                    AccessRecord.PhotoSyncStatus.PENDING
                }
                record.photoUrl = record.sourcePhotoUrl
                accessRecordRepository.save(record)
                seen[key] = record
                record
            } else {
                existing.carNumber = record.carNumber
                existing.inAndOut = record.inAndOut
                existing.inAndOutTime = record.inAndOutTime
                existing.admissionTicketNumber = record.admissionTicketNumber
                existing.departmentName = record.departmentName
                existing.vehicleTypeName = record.vehicleTypeName
                existing.passType = record.passType
                existing.releaseInstructions = record.releaseInstructions
                existing.releaseChannel = record.releaseChannel
                existing.operatorName = record.operatorName
                existing.carOwnerName = record.carOwnerName
                existing.gateName = record.gateName
                val sourceChanged = existing.sourcePhotoUrl != record.sourcePhotoUrl
                existing.sourcePhotoUrl = record.sourcePhotoUrl
                if (sourceChanged && record.sourcePhotoUrl?.trim().isNullOrBlank()) {
                    existing.photoSyncStatus = AccessRecord.PhotoSyncStatus.NOT_AVAILABLE
                    existing.photoUrl = record.sourcePhotoUrl
                } else if (sourceChanged || existing.photoSyncStatus !in setOf(
                        AccessRecord.PhotoSyncStatus.LOCAL,
                        AccessRecord.PhotoSyncStatus.PENDING
                    )) {
                    existing.photoSyncStatus = AccessRecord.PhotoSyncStatus.PENDING
                    existing.photoUrl = record.sourcePhotoUrl
                }
                existing.feeAmount = record.feeAmount
                existing.recordStatus = record.recordStatus
                existing.sourceRecordId = record.sourceRecordId
                accessRecordRepository.save(existing)
                seen[key] = existing
                existing
            }
            val normalizedPlate = PlateNumbers.normalize(record.carNumber)
            platesByNormalizedNumber[normalizedPlate]?.let { plate ->
                plate.carBrand = carBrand
                parkingPlateRepository.save(plate)
            }
            processed++
            if (stored.photoSyncStatus == AccessRecord.PhotoSyncStatus.PENDING) {
                photoTasks.add(
                    VehiclePhotoDownloadCoordinator.PhotoDownloadTask(
                        recordId = requireNotNull(stored.id),
                        sourcePhotoUrl = stored.sourcePhotoUrl,
                        plateNumber = stored.carNumber,
                    )
                )
            }
        }
        val downloadResult = coordinator.downloadBatch(photoTasks)
        val localPhotoCount = downloadResult.successCount
        val failedPhotoCount = downloadResult.failedCount
        return ProcessResult(processed, localPhotoCount, failedPhotoCount)
    }
    
    
    private fun parseRecord(node: JsonNode): AccessRecord? {
        val time = firstText(node, "capTime", "cap_time", "inAndOutTime", "in_and_out_time", "captureTime", "time")
            ?.let(::parseTime)
        if (time == null) {
            logger.warn("忽略缺少有效时间的 Keytop 车辆进出记录：{}", node)
            return null
        }
        val direction = parseDirection(node) ?: run {
            logger.warn("忽略缺少进出方向的 Keytop 车辆进出记录：{}", node)
            return null
        }
        val trafficId = firstText(node, "trafficId", "traffic_id", "recordId", "record_id", "id")
        val capFlag = firstText(node, "capFlag", "cap_flag", "inAndOut", "in_and_out", "direction")
        val carSerial = firstText(node, "carSerial", "car_serial", "serialNo")
        val nodeId = firstText(node, "nodeId", "node_id")
        return AccessRecord().apply {
            carNumber = firstText(node, "plateNo", "plate_no", "carNumber", "car_number", "carNo")
            inAndOut = direction
            inAndOutTime = time
            sourceRecordId = buildSourceRecordId(trafficId, capFlag, time, carSerial, nodeId, carNumber, direction)
            admissionTicketNumber = firstText(node, "cardNo", "card_no") ?: carSerial
            departmentName = firstText(
                node,
                "dept",
                "department",
                "departmentName",
                "department_name",
                "deptName",
                "dept_name",
                "orgName",
                "org_name"
            )
            vehicleTypeName = parseVehicleTypeName(node)
            passType = parsePassType(node)
            releaseInstructions =
                firstText(node, "passDesc", "pass_desc", "passRemark", "pass_remark", "remark", "releaseInstructions")
            releaseChannel = parseReleaseChannel(node)
            operatorName = firstText(node, "operName", "oper_name", "operator", "operatorName", "operator_name")
            carOwnerName = firstText(node, "carOwnerName", "car_owner_name", "ownerName", "owner_name", "owner")
            gateName = firstText(
                node,
                "gate",
                "gateName",
                "gate_name",
                "capPlace",
                "cap_place",
                "laneName",
                "lane_name",
                "channelName",
                "channel_name",
                "placeName",
                "place_name"
            )
            sourcePhotoUrl = firstText(
                node,
                "imgInfo",
                "img_info",
                "photo",
                "photoUrl",
                "photo_url",
                "imageUrl",
                "image_url",
                "pictureUrl",
                "picture_url",
                "picUrl",
                "pic_url",
                "captureUrl",
                "capture_url"
            )
            feeAmount = firstText(
                node,
                "amount",
                "fee",
                "feeAmount",
                "fee_amount",
                "chargeAmount",
                "charge_amount"
            )?.toBigDecimalOrNull() ?: BigDecimal.ZERO
            recordStatus = firstText(node, "status", "recordStatus", "record_status") ?: "正常"
        }
    }
    
    
    private fun parseDirection(node: JsonNode): AccessRecord.InAndOut? {
        val raw = firstText(node, "inAndOut", "in_and_out", "direction", "capFlag", "cap_flag", "type")
            ?.trim()?.lowercase() ?: return null
        return when {
            raw in setOf(
                "out",
                "exit",
                "leave",
                "1",
                "2",
                "出",
                "出场"
            ) || raw.contains("出场") || raw.contains("出口") ->
                AccessRecord.InAndOut.OUT
            
            raw in setOf("in", "entry", "enter", "0", "抓拍", "capture", "3", "入", "入场") ||
                    raw.contains("入场") || raw.contains("入口") || raw.contains("抓拍") ->
                AccessRecord.InAndOut.IN
            
            else -> null
        }
    }
    
    
    private fun parsePassType(node: JsonNode): String? {
        val raw = firstText(node, "passType", "pass_type", "releaseType", "release_type")
            ?.trim() ?: return null
        return when {
            raw == "1" || raw.contains("auto", ignoreCase = true) || raw.contains("自动") -> "自动放行"
            raw == "2" || raw.contains("manual", ignoreCase = true) || raw.contains("人工") -> "人工放行"
            raw == "3" || raw.contains("remote", ignoreCase = true) || raw.contains("远程") -> "远程放行"
            else -> raw
        }
    }
    
    
    private fun parseVehicleTypeName(node: JsonNode): String? = AccessRecord.displayVehicleTypeName(
        firstText(node, "vehicleType", "vehicle_type", "carTypeName", "car_type_name", "carType", "car_type")?.trim(),
    )
    
    
    private fun buildSourceRecordId(
        trafficId: String?,
        capFlag: String?,
        time: LocalDateTime,
        carSerial: String?,
        nodeId: String?,
        carNumber: String?,
        direction: AccessRecord.InAndOut,
    ): String = listOf(
        "v2",
        trafficId?.trim().orEmpty().ifBlank { carNumber.orEmpty() },
        capFlag?.trim().orEmpty().ifBlank { direction.name },
        time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
        carSerial?.trim().orEmpty(),
        nodeId?.trim().orEmpty(),
    ).joinToString("|")
    
    
    private fun retryFailedPhotos(coordinator: VehiclePhotoDownloadCoordinator) {
        val retryRecords = accessRecordRepository.findTop100ByPhotoSyncStatusInOrderByIdAsc(
            listOf(AccessRecord.PhotoSyncStatus.FAILED, AccessRecord.PhotoSyncStatus.PENDING)
        )
        if (retryRecords.isEmpty()) return

        val retryTasks = retryRecords.map { record ->
            VehiclePhotoDownloadCoordinator.PhotoDownloadTask(
                recordId = requireNotNull(record.id),
                sourcePhotoUrl = record.sourcePhotoUrl,
                plateNumber = record.carNumber,
            )
        }

        val result = coordinator.downloadBatch(retryTasks)
        logger.info(
            "重试历史失败图片：提交 {} 个，成功 {}，失败 {}，跳过 {}",
            retryTasks.size,
            result.successCount,
            result.failedCount,
            result.skippedCount
        )
    }


    private fun parseReleaseChannel(node: JsonNode): AccessRecord.ReleaseChannel? {
        val raw = firstText(node, "passType", "pass_type", "releaseChannel", "release_channel")
            ?.trim()?.lowercase() ?: return null
        return when {
            raw.contains("manual") || raw.contains("人工") || raw == "2" -> AccessRecord.ReleaseChannel.MANUAL
            raw.contains("remote") || raw.contains("远程") || raw == "3" -> AccessRecord.ReleaseChannel.REMOTE
            raw.contains("auto") || raw.contains("自动") || raw == "1" -> AccessRecord.ReleaseChannel.AUTOMATIC
            else -> AccessRecord.ReleaseChannel.UNKNOWN
        }
    }
    
    
    private fun parseTime(raw: String): LocalDateTime? {
        val value = raw.trim()
        return try {
            LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        } catch (_: DateTimeParseException) {
            try {
                LocalDateTime.parse(value, PLATFORM_TIME)
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }
    
    
    private fun firstText(node: JsonNode, vararg names: String): String? = names.asSequence()
        .mapNotNull { node.get(it) }
        .firstOrNull { !it.isNull && !it.isMissingNode && it.asString().isNotBlank() }
        ?.asString()
    
    
    private fun parseData(data: JsonNode?): ParsedData {
        if (data == null || data.isNull) return ParsedData(emptyList(), 0)
        var container = if (data.isTextual) objectMapper.readTree(data.asString()) else data
        val nestedData = container.get("data")
        if (nestedData != null && !nestedData.isNull &&
            (nestedData.isObject || nestedData.isArray || nestedData.isTextual)
        ) {
            container = if (nestedData.isTextual) objectMapper.readTree(nestedData.asString()) else nestedData
        }
        if (container.isArray) return ParsedData(container.toList(), null)
        val rawRecords = container.get("detailList") ?: container.get("detail_list")
        ?: container.get("records") ?: container.get("list")
        val recordsNode =
            if (rawRecords?.isTextual == true) objectMapper.readTree(rawRecords.asString()) else rawRecords
        val records = if (recordsNode?.isArray == true) recordsNode.toList() else emptyList()
        val totalCount = firstText(container, "totalCount", "total_count", "total", "count")?.toIntOrNull()
        return ParsedData(records, totalCount)
    }
    
    private data class ParsedData(val records: List<JsonNode>, val totalCount: Int?)
    
    private data class ProcessResult(
        val processedCount: Int,
        val localPhotoCount: Int,
        val failedPhotoCount: Int,
    )

    companion object {
        val logger = LoggerFactory.getLogger(SynCarCapInfoTask::class.java)
        val executionLock = ReentrantLock()
        val PLATFORM_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        const val SYNC_KEY = "keytop.car_cap_info"
        const val TASK_KEY = "car_cap_info.sync"
        const val TASK_NAME = "车辆进出记录同步"
        const val RECONCILIATION_TASK_KEY = "car_cap_info.reconciliation"
        const val RECONCILIATION_TASK_NAME = "车辆进出记录补偿同步"
        const val INITIAL_SYNC_DAYS = 1L
    }
}
