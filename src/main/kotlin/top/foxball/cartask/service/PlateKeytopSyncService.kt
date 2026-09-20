package top.foxball.cartask.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import top.foxball.cartask.audit.AuditAction
import top.foxball.cartask.audit.AuditCommand
import top.foxball.cartask.audit.AuditService
import top.foxball.cartask.entity.AuditEvent
import top.foxball.cartask.entity.ParkingPlate
import top.foxball.cartask.entity.ParkingPlateKeytopSyncTask
import top.foxball.cartask.keytop.KeytopCardInfo
import top.foxball.cartask.keytop.KeytopPayCarCardFeeRequest
import top.foxball.cartask.keytop.KeytopPlateNo
import top.foxball.cartask.keytop.KeytopProperties
import top.foxball.cartask.keytop.KeytopResponse
import top.foxball.cartask.keytop.KeytopService
import top.foxball.cartask.keytop.KeytopSyncRateLimiter
import top.foxball.cartask.repository.ParkingPlateKeytopSyncTaskRepository
import top.foxball.cartask.repository.ParkingOwnerRepository
import top.foxball.cartask.repository.ParkingPlateRepository
import top.foxball.cartask.shared.PlateNumbers
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean

data class PlateKeytopSyncResult(
    val plateId: Long,
    val status: ParkingPlate.KeytopSyncStatus,
    val taskId: Long?,
    val message: String?,
)

@Service
class PlateKeytopSyncService(
    private val plateRepository: ParkingPlateRepository,
    private val ownerRepository: ParkingOwnerRepository,
    private val auditService: AuditService,
    private val taskRepository: ParkingPlateKeytopSyncTaskRepository,
    private val keytopService: KeytopService,
    private val keytopProperties: KeytopProperties,
    private val objectMapper: ObjectMapper,
    private val keytopSyncRateLimiter: KeytopSyncRateLimiter? = null,
) {
    private val running = AtomicBoolean(false)
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun enqueueAfterChange(
        plate: ParkingPlate,
        previousPlate: String?,
        previousStatus: Int,
        previousCardId: Long?,
    ): PlateKeytopSyncResult {
        val plateId = requireNotNull(plate.id) { "车牌必须先保存后才能同步月卡" }
        val normalizedPlate = requireNotNull(PlateNumbers.normalize(plate.plate)) { "车牌号不能为空" }
        val version = plate.keytopSyncVersion + 1
        plate.keytopSyncVersion = version
        plate.keytopLastError = null
        val task = ParkingPlateKeytopSyncTask().apply {
            this.plateId = plateId
            this.version = version
            this.plateNo = normalizedPlate
            this.ownerName = plate.owner
            this.ownerPhone = findOwnerPhone(plate.ownerId)
            this.departmentCode = null
            this.idempotencyKey = "$plateId:$version"
        }
        if (plate.status == STATUS_ENABLED) {
            val normalizedPrevious = PlateNumbers.normalize(previousPlate)
            val replacingCard = previousStatus == STATUS_ENABLED && normalizedPrevious != null && normalizedPrevious != normalizedPlate
            task.operation = ParkingPlateKeytopSyncTask.Operation.UPSERT
            task.oldPlateNo = normalizedPrevious?.takeIf {
                replacingCard || (previousStatus != STATUS_ENABLED && previousCardId != null)
            }
            task.keytopCardId = if (previousStatus == STATUS_ENABLED) previousCardId ?: plate.keytopCardId else null
            task.keytopPlateId = if (previousStatus == STATUS_ENABLED) plate.keytopPlateId else null
            task.status = ParkingPlateKeytopSyncTask.Status.PENDING
            plate.keytopSyncStatus = ParkingPlate.KeytopSyncStatus.PENDING
        } else if (previousStatus == STATUS_ENABLED || previousCardId != null || plate.keytopCardId != null) {
            task.operation = ParkingPlateKeytopSyncTask.Operation.DELETE
            task.oldPlateNo = normalizedPlate
            task.keytopCardId = previousCardId ?: plate.keytopCardId
            task.keytopPlateId = plate.keytopPlateId
            task.status = ParkingPlateKeytopSyncTask.Status.PENDING
            plate.keytopSyncStatus = ParkingPlate.KeytopSyncStatus.DELETE_PENDING
        } else {
            plate.keytopSyncStatus = ParkingPlate.KeytopSyncStatus.DELETED
            plate.keytopCardId = null
            plate.keytopPlateId = null
            plateRepository.save(plate)
            return PlateKeytopSyncResult(plateId, plate.keytopSyncStatus, null, "车牌已停用，无需创建月卡")
        }
        plateRepository.save(plate)
        val savedTask = taskRepository.save(task)
        return PlateKeytopSyncResult(plateId, plate.keytopSyncStatus, savedTask.id, "月卡同步任务已创建")
    }

    @Transactional
    fun enqueueDelete(plate: ParkingPlate): PlateKeytopSyncResult {
        val previousStatus = plate.status
        val previousCardId = plate.keytopCardId
        plate.status = STATUS_DISABLED
        return enqueueAfterChange(
            plate = plate,
            previousPlate = plate.plate,
            previousStatus = previousStatus,
            previousCardId = previousCardId,
        )
    }

    @Transactional
    fun retry(plateId: Long): PlateKeytopSyncResult {
        val plate = plateRepository.findById(plateId).orElseThrow { IllegalArgumentException("车牌不存在") }
        val previousStatus = if (plate.status == STATUS_ENABLED) STATUS_ENABLED else STATUS_DISABLED
        return enqueueAfterChange(plate, plate.plate, previousStatus, plate.keytopCardId)
    }

    fun processBatch(): Int {
        if (!running.compareAndSet(false, true)) return 0
        return try {
            val snapshot = keytopSyncRateLimiter?.snapshot()
            val runBatch = {
                ensurePendingTasks()
                resetStaleTasks()
                var processed = 0
                while (processed < keytopProperties.plateSyncBatchSize) {
                    val task = claimNext() ?: break
                    process(task)
                    processed++
                }
                processed
            }
            if (snapshot == null) runBatch() else keytopSyncRateLimiter.withSnapshot(snapshot, runBatch)
        } finally {
            running.set(false)
        }
    }

    private fun ensurePendingTasks() {
        plateRepository.findAll().forEach { plate ->
            if (plate.status == STATUS_ENABLED && plate.keytopSyncStatus == ParkingPlate.KeytopSyncStatus.PENDING) {
                val version = plate.keytopSyncVersion
                val existing = if (version > 0) taskRepository.findByPlateIdAndVersionAndOperation(
                    requireNotNull(plate.id),
                    version,
                    ParkingPlateKeytopSyncTask.Operation.UPSERT,
                ) else null
                if (existing == null) enqueueAfterChange(plate, plate.plate, STATUS_DISABLED, null)
            } else if (plate.status != STATUS_ENABLED && plate.keytopSyncStatus == ParkingPlate.KeytopSyncStatus.PENDING) {
                plate.keytopSyncStatus = ParkingPlate.KeytopSyncStatus.DELETED
                plateRepository.save(plate)
            }
        }
    }

    fun reconcile(): Int {
        val snapshot = keytopSyncRateLimiter?.snapshot()
        val reconcile = {
            var queued = 0
            plateRepository.findAll().filter { it.status == STATUS_ENABLED }.forEach { plate ->
                val normalized = PlateNumbers.normalize(plate.plate) ?: return@forEach
                val response = runCatching { keytopService.getCarCardInfo(normalized) }.getOrElse {
                    logger.warn("对账查询 Keytop 月卡失败：plate={}", normalized, it)
                    return@forEach
                }
                val card = if (response.code == SUCCESS_CODE) parseCard(response.data, normalized) else null
                if (card?.cardId != plate.keytopCardId || card?.plateNo != normalized || plate.keytopSyncStatus != ParkingPlate.KeytopSyncStatus.SYNCED) {
                    enqueueAfterChange(plate, plate.plate, STATUS_ENABLED, plate.keytopCardId)
                    queued++
                }
            }
            queued
        }
        return if (snapshot == null) reconcile() else keytopSyncRateLimiter.withSnapshot(snapshot, reconcile)
    }

    private fun claimNext(): ParkingPlateKeytopSyncTask? {
        val statuses = listOf(ParkingPlateKeytopSyncTask.Status.PENDING, ParkingPlateKeytopSyncTask.Status.RETRYING)
        val now = LocalDateTime.now()
        val task = taskRepository.findFirstByStatusInAndNextAttemptAtIsNullOrderByIdAsc(statuses)
            ?: taskRepository.findFirstByStatusInAndNextAttemptAtLessThanEqualOrderByIdAsc(statuses, now)
            ?: return null
        task.status = ParkingPlateKeytopSyncTask.Status.PROCESSING
        task.attempts += 1
        task.processingStartedAt = now
        task.nextAttemptAt = null
        return taskRepository.saveAndFlush(task)
    }

    private fun resetStaleTasks() {
        val threshold = LocalDateTime.now().minusMinutes(10)
        taskRepository.findByStatus(ParkingPlateKeytopSyncTask.Status.PROCESSING)
            .filter { it.processingStartedAt?.isBefore(threshold) == true }
            .forEach {
                it.status = ParkingPlateKeytopSyncTask.Status.RETRYING
                it.nextAttemptAt = LocalDateTime.now()
                taskRepository.save(it)
            }
    }

    private fun process(task: ParkingPlateKeytopSyncTask) {
        try {
            if (task.operation == ParkingPlateKeytopSyncTask.Operation.DELETE) syncDelete(task) else syncUpsert(task)
            task.status = ParkingPlateKeytopSyncTask.Status.SUCCEEDED
            task.lastError = null
            taskRepository.save(task)
        } catch (exception: BlockedSyncException) {
            markFailure(task, exception.message ?: "月卡同步数据冲突", blocked = true)
        } catch (exception: RuntimeException) {
            markFailure(task, exception.message ?: exception.javaClass.simpleName, blocked = false)
        }
    }

    private fun syncUpsert(task: ParkingPlateKeytopSyncTask) {
        val plateId = requireNotNull(task.plateId)
        val plate = plateRepository.findById(plateId).orElseThrow { BlockedSyncException("车牌已不存在") }
        if (plate.keytopSyncVersion > task.version || plate.status != STATUS_ENABLED) return

        val requiresActivation = task.keytopCardId == null || task.oldPlateNo != null
        if (task.oldPlateNo != null) {
            deleteCard(task.keytopCardId, task.oldPlateNo!!)
            task.keytopCardId = null
            task.keytopPlateId = null
        }

        val normalized = requireNotNull(PlateNumbers.normalize(plate.plate)) { "车牌号不能为空" }
        var card = task.keytopCardId?.let { readCardById(it, normalized) } ?: readCardByPlate(normalized)
        val existingOwner = card?.cardId?.let { plateRepository.findByKeytopCardId(it) }
        if (existingOwner != null && existingOwner.id != plate.id) {
            throw BlockedSyncException("Keytop 月卡 ${card.cardId} 已绑定其它车牌")
        }

        var created = false
        if (card == null) {
            requireSuccess(
                keytopService.addCarCardNo(
                    userId = keytopProperties.plateSyncOperatorId,
                    userName = keytopProperties.plateSyncOperatorName,
                    cardInfo = cardInfo(plate),
                    carLotList = emptyList(),
                    plateNoInfo = listOf(KeytopPlateNo(plateNo = normalized)),
                ),
                "月卡新增",
            )
            created = true
            card = readCardByPlate(normalized) ?: throw IllegalStateException("月卡新增成功但未读回卡号")
        }
        val cardId = requireNotNull(card.cardId) { "Keytop 月卡缺少 cardId" }
        if (!created) {
            val plateItemId = card.plateId ?: task.keytopPlateId
                ?: throw BlockedSyncException("Keytop 月卡缺少车牌项 ID，无法安全修改")
            requireSuccess(
                keytopService.modifyCarCardNo(
                    userId = keytopProperties.plateSyncOperatorId,
                    userName = keytopProperties.plateSyncOperatorName,
                    cardInfo = cardInfo(plate).copy(cardId = cardId),
                    carLotList = emptyList(),
                    plateNoInfo = listOf(KeytopPlateNo(normalized, id = plateItemId, plateState = PLATE_STATE_ENABLED)),
                ),
                "月卡修改",
            )
        }
        if (requiresActivation) {
            val validFrom = keytopProperties.plateSyncDefaultValidFrom ?: LocalDateTime.now()
            requireSuccess(
                keytopService.payCarCardFee(
                    KeytopPayCarCardFeeRequest(
                        userId = keytopProperties.plateSyncOperatorId,
                        userName = keytopProperties.plateSyncOperatorName,
                        cardId = cardId,
                        carType = MONTHLY_CAR_TYPE,
                        validFrom = validFrom,
                        validTo = keytopProperties.plateSyncDefaultValidTo,
                    ),
                ),
                "月卡启用",
            )
        }
        task.keytopCardId = cardId
        task.keytopPlateId = card.plateId
        markPlateSynced(task, cardId, card.plateId)
    }

    private fun syncDelete(task: ParkingPlateKeytopSyncTask) {
        deleteCard(task.keytopCardId, task.oldPlateNo ?: task.plateNo)
        task.plateId?.let { plateId ->
            val plate = plateRepository.findById(plateId).orElse(null) ?: return
            if (plate.keytopSyncVersion == task.version) {
                plate.keytopSyncStatus = ParkingPlate.KeytopSyncStatus.DELETED
                plate.keytopCardId = null
                plate.keytopPlateId = null
                plate.keytopLastSyncedAt = LocalDateTime.now()
                plate.keytopLastError = null
                plateRepository.save(plate)
            }
        }
        runCatching {
            auditService.record(
                AuditCommand(
                    action = AuditAction.PLATE_KEYTOP_SYNCED,
                    targetType = "parking_plate",
                    targetId = task.plateId?.toString(),
                    result = AuditEvent.Result.SUCCESS,
                    targetSummary = mapOf("plate" to (task.oldPlateNo ?: task.plateNo), "card_id" to task.keytopCardId),
                    afterData = mapOf("operation" to "DELETE", "keytop_sync_status" to "DELETED"),
                    sourceSystem = "SCHEDULER",
                    idempotencyKey = "plate-keytop:${task.id}:delete-success",
                ),
            )
        }.onFailure { logger.warn("车牌月卡删除成功审计写入失败：taskId={}", task.id, it) }
    }

    private fun deleteCard(cardId: Long?, plateNo: String) {
        val card = cardId?.let { readCardById(it, plateNo) } ?: readCardByPlate(plateNo)
        val id = card?.cardId ?: return
        requireSuccess(keytopService.delCarCardInfo(id), "月卡删除")
    }

    private fun markPlateSynced(task: ParkingPlateKeytopSyncTask, cardId: Long, plateItemId: Long?) {
        val plateId = requireNotNull(task.plateId)
        val plate = plateRepository.findById(plateId).orElseThrow { BlockedSyncException("车牌已不存在") }
        if (plate.keytopSyncVersion != task.version || plate.status != STATUS_ENABLED) return
        plate.keytopCardId = cardId
        plate.keytopPlateId = plateItemId
        plate.keytopSyncStatus = ParkingPlate.KeytopSyncStatus.SYNCED
        plate.keytopLastSyncedAt = LocalDateTime.now()
        plate.keytopLastError = null
        plateRepository.save(plate)
        runCatching {
            auditService.record(
                AuditCommand(
                    action = AuditAction.PLATE_KEYTOP_SYNCED,
                    targetType = "parking_plate",
                    targetId = plate.id.toString(),
                    result = AuditEvent.Result.SUCCESS,
                    targetSummary = mapOf("plate" to plate.plate, "card_id" to cardId),
                    afterData = mapOf("keytop_sync_status" to plate.keytopSyncStatus.name),
                    sourceSystem = "SCHEDULER",
                    idempotencyKey = "plate-keytop:${plate.id}:${task.version}:success",
                ),
            )
        }.onFailure { logger.warn("车牌月卡成功审计写入失败：plateId={}", plate.id, it) }
    }

    private fun markFailure(task: ParkingPlateKeytopSyncTask, message: String, blocked: Boolean) {
        val clean = message.take(2048)
        task.lastError = clean
        task.status = if (blocked) ParkingPlateKeytopSyncTask.Status.BLOCKED
        else if (task.attempts >= keytopProperties.plateSyncMaxAttempts) ParkingPlateKeytopSyncTask.Status.DEAD
        else ParkingPlateKeytopSyncTask.Status.RETRYING
        task.nextAttemptAt = if (task.status == ParkingPlateKeytopSyncTask.Status.RETRYING) {
            LocalDateTime.now().plus(retryDelay(task.attempts))
        } else null
        taskRepository.save(task)
        task.plateId?.let { plateId ->
            val plate = plateRepository.findById(plateId).orElse(null) ?: return@let
            if (plate.keytopSyncVersion == task.version) {
                plate.keytopSyncStatus = when {
                    blocked -> ParkingPlate.KeytopSyncStatus.BLOCKED
                    task.operation == ParkingPlateKeytopSyncTask.Operation.DELETE -> ParkingPlate.KeytopSyncStatus.DELETE_PENDING
                    else -> ParkingPlate.KeytopSyncStatus.FAILED
                }
                plate.keytopLastError = clean
                plateRepository.save(plate)
            }
        }
        logger.error("车牌月卡同步失败：taskId={}, plateId={}, status={}, error={}", task.id, task.plateId, task.status, clean)
        runCatching {
            auditService.record(
                AuditCommand(
                    action = AuditAction.PLATE_KEYTOP_SYNCED,
                    targetType = "parking_plate",
                    targetId = task.plateId?.toString(),
                    result = AuditEvent.Result.FAILED,
                    reasonCode = if (blocked) "KEYTOP_SYNC_BLOCKED" else "KEYTOP_SYNC_FAILED",
                    reason = clean,
                    targetSummary = mapOf("plate" to task.plateNo, "card_id" to task.keytopCardId),
                    afterData = mapOf("keytop_sync_status" to task.status.name),
                    sourceSystem = "SCHEDULER",
                    idempotencyKey = "plate-keytop:${task.id}:${task.attempts}:failure",
                ),
            )
        }.onFailure { logger.warn("车牌月卡失败审计写入失败：taskId={}", task.id, it) }
    }

    private fun retryDelay(attempt: Int): Duration = when (attempt.coerceAtMost(5)) {
        1 -> Duration.ofMinutes(1)
        2 -> Duration.ofMinutes(5)
        3 -> Duration.ofMinutes(15)
        4 -> Duration.ofHours(1)
        else -> Duration.ofHours(6)
    }

    private fun readCardById(cardId: Long, plateNo: String): CardDetails? {
        val response = keytopService.getCarCardInfo(cardId)
        if (isNotFound(response)) return null
        requireSuccess(response, "月卡查询")
        return parseCard(response.data, plateNo)
    }

    private fun readCardByPlate(plateNo: String): CardDetails? {
        val response = keytopService.getCarCardInfo(plateNo)
        if (isNotFound(response)) return null
        requireSuccess(response, "月卡查询")
        return parseCard(response.data, plateNo)
    }

    private fun isNotFound(response: KeytopResponse?): Boolean {
        val message = response?.message.orEmpty()
        return response != null && response.code != SUCCESS_CODE &&
                listOf("不存在", "未找到", "无数据", "没有卡", "not found").any { message.contains(it, ignoreCase = true) }
    }

    private fun parseCard(data: JsonNode?, expectedPlate: String): CardDetails? {
        if (data == null || data.isNull) return null
        val raw = if (data.isString) objectMapper.readTree(data.asString()) else data
        val container = raw.get("data")?.takeIf { !it.isNull } ?: raw
        val card = container.get("cardInfo")?.takeIf { !it.isNull }
            ?: container.get("card_info")?.takeIf { !it.isNull }
            ?: container
        val cardId = firstLong(card, "cardId", "card_id") ?: return null
        val plateNode = card.get("plateNoInfo") ?: card.get("plate_no_info")
        val plates: JsonNode? = when {
            plateNode == null || plateNode.isNull -> null
            plateNode.isString -> objectMapper.readTree(plateNode.asString())
            else -> plateNode
        }
        val item = if (plates?.isArray == true) plates.firstOrNull { PlateNumbers.normalize(firstText(it, "plateNo", "plate_no")) == expectedPlate } else null
        val returnedPlate = firstText(item, "plateNo", "plate_no") ?: expectedPlate
        return CardDetails(cardId, PlateNumbers.normalize(returnedPlate), firstLong(item, "id", "plateId", "plate_id"))
    }

    private fun cardInfo(plate: ParkingPlate) = KeytopCardInfo(
        cardName = "车牌月卡-${requireNotNull(plate.id)}",
        useName = plate.owner,
        tel = findOwnerPhone(plate.ownerId),
        roomId = "",
        remak = "carTask plateId=${plate.id}",
    )

    private fun findOwnerPhone(ownerId: Long): String = ownerRepository.findById(ownerId).orElse(null)?.phone.orEmpty()

    private fun requireSuccess(response: KeytopResponse?, action: String) {
        require(response != null && response.code == SUCCESS_CODE) {
            "科拓${action}失败：${response?.code ?: "无响应"} ${response?.message.orEmpty()}".trim()
        }
    }

    private fun firstText(node: JsonNode?, vararg names: String): String? = node?.let { source ->
        names.asSequence().mapNotNull { source.get(it)?.takeIf { value -> !value.isNull }?.asString() }
            .firstOrNull { it.isNotBlank() }
    }

    private fun firstLong(node: JsonNode?, vararg names: String): Long? = firstText(node, *names)?.toLongOrNull()

    private data class CardDetails(val cardId: Long, val plateNo: String?, val plateId: Long?)
    private class BlockedSyncException(message: String) : IllegalStateException(message)

    private companion object {
        const val STATUS_ENABLED = 1
        const val STATUS_DISABLED = 0
        const val SUCCESS_CODE = 0
        const val MONTHLY_CAR_TYPE = 1
        const val PLATE_STATE_ENABLED = 1
    }
}
