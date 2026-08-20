package top.foxball.cartask.task

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import top.foxball.cartask.entity.AccessRecord
import top.foxball.cartask.keytop.KeytopProperties
import top.foxball.cartask.keytop.KeytopResponse
import top.foxball.cartask.keytop.KeytopService
import top.foxball.cartask.repository.AccessRecordRepository
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.ceil

/** 从科拓同步车辆进场、出场和抓拍记录到本地车辆进出流水。 */
@Component
class SynCarCapInfoTask(
    private val keytopService: KeytopService,
    private val accessRecordRepository: AccessRecordRepository,
    private val objectMapper: ObjectMapper,
    private val keytopProperties: KeytopProperties,
) {
    @Scheduled(cron = "#{@keytopProperties.carCapInfoSyncCron}", zone = "Asia/Shanghai")
    @Transactional
    fun synCarCapInfoList() {
        if (!executionLock.tryLock()) {
            logger.warn("车辆进出记录同步仍在执行，本次跳过")
            return
        }
        try {
            require(keytopProperties.carCapInfoPageSize in 1..1000) {
                "车辆进出记录同步分页大小必须在 1 到 1000 之间"
            }
            require(keytopProperties.carCapInfoLookbackMinutes >= 0) {
                "车辆进出记录同步回看分钟数不能为负数"
            }
            val startTime = accessRecordRepository
                .findTopByOrderByInAndOutTimeDescIdDesc()
                ?.inAndOutTime
                ?.minusMinutes(keytopProperties.carCapInfoLookbackMinutes)
            val firstPage = keytopService.getCarInoutInfo(
                pageIndex = 1,
                pageSize = keytopProperties.carCapInfoPageSize,
                startTime = startTime,
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
            val seen = mutableMapOf<RecordKey, AccessRecord>()
            var synchronizedCount = processRecords(firstData.records, seen)
            var pageIndex = 2
            while (pageIndex <= pages || (totalCount == null && firstData.records.size >= keytopProperties.carCapInfoPageSize)) {
                val response = keytopService.getCarInoutInfo(
                    pageIndex = pageIndex,
                    pageSize = keytopProperties.carCapInfoPageSize,
                    startTime = startTime,
                )
                require(response.code == 0) {
                    "Keytop 车辆进出接口第 ${pageIndex} 页返回失败：${response.code ?: "未知"} ${response.message.orEmpty()}".trim()
                }
                val data = parseData(response.data)
                synchronizedCount += processRecords(data.records, seen)
                if (totalCount == null && data.records.size < keytopProperties.carCapInfoPageSize) break
                pageIndex++
            }
            logger.info("车辆进出记录同步完成：处理 {} 条，起始时间：{}", synchronizedCount, startTime)
        } catch (exception: RuntimeException) {
            logger.error("车辆进出记录同步失败", exception)
        } finally {
            executionLock.unlock()
        }
    }

    private fun processRecords(
        records: List<JsonNode>,
        seen: MutableMap<RecordKey, AccessRecord>,
    ): Int {
        var processed = 0
        records.forEach { node ->
            val record = parseRecord(node) ?: return@forEach
            val key = RecordKey(record.carNumber, record.inAndOut, record.inAndOutTime)
            val existing = seen[key] ?: accessRecordRepository.findByIdentity(
                record.carNumber,
                record.inAndOut,
                record.inAndOutTime,
            )
            if (existing == null) {
                accessRecordRepository.save(record)
                seen[key] = record
            } else {
                existing.carNumber = record.carNumber
                existing.inAndOut = record.inAndOut
                existing.inAndOutTime = record.inAndOutTime
                existing.admissionTicketNumber = record.admissionTicketNumber
                existing.releaseInstructions = record.releaseInstructions
                existing.releaseChannel = record.releaseChannel
                existing.operatorName = record.operatorName
                existing.carOwnerName = record.carOwnerName
                accessRecordRepository.save(existing)
                seen[key] = existing
            }
            processed++
        }
        return processed
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
        return AccessRecord().apply {
            carNumber = firstText(node, "plateNo", "plate_no", "carNumber", "car_number", "carNo")
            inAndOut = direction
            inAndOutTime = time
            admissionTicketNumber = firstText(node, "cardNo", "card_no", "carSerial", "car_serial", "serialNo")
            releaseInstructions = firstText(node, "passRemark", "pass_remark", "remark", "releaseInstructions")
            releaseChannel = parseReleaseChannel(node)
            operatorName = firstText(node, "operName", "oper_name", "operator", "operatorName", "operator_name")
            carOwnerName = firstText(node, "carOwnerName", "car_owner_name", "ownerName", "owner_name")
        }
    }

    private fun parseDirection(node: JsonNode): AccessRecord.InAndOut? {
        val raw = firstText(node, "inAndOut", "in_and_out", "direction", "capFlag", "cap_flag", "type")
            ?.trim()?.lowercase() ?: return null
        return when {
            raw in setOf("out", "exit", "leave", "2", "出", "出场") || raw.contains("出场") || raw.contains("出口") ->
                AccessRecord.InAndOut.OUT
            raw in setOf("in", "entry", "enter", "1", "0", "抓拍", "capture", "3", "入", "入场") ||
                raw.contains("入场") || raw.contains("入口") || raw.contains("抓拍") ->
                AccessRecord.InAndOut.IN
            else -> null
        }
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
        val recordsNode = if (rawRecords?.isTextual == true) objectMapper.readTree(rawRecords.asString()) else rawRecords
        val records = if (recordsNode?.isArray == true) recordsNode.toList() else emptyList()
        val totalCount = firstText(container, "totalCount", "total_count", "total", "count")?.toIntOrNull()
        return ParsedData(records, totalCount)
    }

    private data class ParsedData(val records: List<JsonNode>, val totalCount: Int?)

    private data class RecordKey(
        val carNumber: String?,
        val inAndOut: AccessRecord.InAndOut,
        val inAndOutTime: LocalDateTime,
    )

    private companion object {
        val logger = LoggerFactory.getLogger(SynCarCapInfoTask::class.java)
        val executionLock = ReentrantLock()
        val PLATFORM_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
