package top.foxball.cartask.service.impl

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import top.foxball.cartask.audit.AuditAction
import top.foxball.cartask.audit.AuditCommand
import top.foxball.cartask.audit.AuditService
import top.foxball.cartask.audit.AuditEventHash
import top.foxball.cartask.entity.AuditEvent
import top.foxball.cartask.repository.AuditEventRepository
import top.foxball.cartask.service.AuditQueryService
import tools.jackson.databind.ObjectMapper
import java.time.LocalDateTime
import java.util.UUID

@Service
class AuditQueryServiceImpl(
    private val repository: AuditEventRepository,
    private val objectMapper: ObjectMapper,
    private val auditService: AuditService,
    private val meterRegistry: MeterRegistry? = null,
) : AuditQueryService {
    override fun list(query: AuditQueryService.Query): AuditQueryService.PageData {
        validate(query, requireTimeRange = true)
        val pageable = PageRequest.of(
            query.page - 1,
            query.pageSize,
            Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("eventId")),
        )
        val page = repository.findAll(specification(query), pageable)
        return AuditQueryService.PageData(page.content.map(::toData), query.page, query.pageSize, page.totalElements)
    }

    override fun get(eventId: UUID): AuditQueryService.EventData = repository.findByEventId(eventId)
        ?.let(::toData)
        ?: throw IllegalArgumentException("审计事件不存在: $eventId")

    override fun export(query: AuditQueryService.Query): List<AuditQueryService.EventData> {
        require(query.occurredFrom != null && query.occurredTo != null) { "导出必须指定开始和结束时间" }
        validate(query.copy(page = 1, pageSize = 100), maxDays = 7, requireTimeRange = true)
        val events = repository.findAll(
            specification(query),
            PageRequest.of(0, EXPORT_LIMIT, Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("eventId"))),
        ).content.map(::toData)
        auditService.record(
            AuditCommand(
                action = AuditAction.SENSITIVE_DATA_EXPORTED,
                targetType = "audit_event",
                result = AuditEvent.Result.SUCCESS,
                targetSummary = mapOf("record_count" to events.size),
                scopeSummary = mapOf(
                    "occurred_from" to query.occurredFrom.toString(),
                    "occurred_to" to query.occurredTo.toString(),
                    "actor_user_id" to query.actorUserId,
                    "action" to query.action,
                    "target_type" to query.targetType,
                ),
            ),
        )
        meterRegistry?.counter("audit_export_records_total")?.increment(events.size.toDouble())
        return events
    }

    override fun verify(partitionKey: String): AuditQueryService.VerificationData {
        require(Regex("\\d{4}-(0[1-9]|1[0-2])").matches(partitionKey)) { "分区必须使用有效的 yyyy-MM 格式" }
        val events = repository.findByPartitionKeyOrderBySequenceNoAsc(partitionKey)
        if (events.isEmpty()) {
            return AuditQueryService.VerificationData(partitionKey, true, 0, null, null, "分区没有审计事件")
        }
        var previousHash: String? = null
        if (events.first().sequenceNo != 1L || events.first().previousHash != null) {
            return AuditQueryService.VerificationData(partitionKey, false, events.size, events.first().sequenceNo, events.last().sequenceNo, "分区首条事件序号或前序摘要无效")
        }
        var expectedSequence = 1L
        events.forEach { event ->
            if (event.partitionKey != partitionKey || event.sequenceNo != expectedSequence || event.previousHash != previousHash || event.eventHash != AuditEventHash.calculate(event)) {
                meterRegistry?.counter("audit_chain_verification_failed_total")?.increment()
                return AuditQueryService.VerificationData(partitionKey, false, events.size, events.first().sequenceNo, events.last().sequenceNo, "序号、摘要链或事件摘要不一致")
            }
            previousHash = event.eventHash
            expectedSequence++
        }
        return AuditQueryService.VerificationData(partitionKey, true, events.size, events.first().sequenceNo, events.last().sequenceNo, "校验通过")
    }

    private fun specification(query: AuditQueryService.Query): Specification<AuditEvent> = Specification { root, _, builder ->
        val predicates = mutableListOf<jakarta.persistence.criteria.Predicate>()
        query.occurredFrom?.let { predicates += builder.greaterThanOrEqualTo(root.get("occurredAt"), it) }
        query.occurredTo?.let { predicates += builder.lessThanOrEqualTo(root.get("occurredAt"), it) }
        query.actorUserId?.let { predicates += builder.equal(root.get<Long>("actorUserId"), it) }
        query.action?.let { predicates += builder.equal(root.get<String>("action"), it) }
        query.targetType?.let { predicates += builder.equal(root.get<String>("targetType"), it) }
        query.targetId?.let { predicates += builder.equal(root.get<String>("targetId"), it) }
        query.result?.let { predicates += builder.equal(root.get<AuditEvent.Result>("result"), it) }
        query.riskLevel?.let { predicates += builder.equal(root.get<AuditEvent.RiskLevel>("riskLevel"), it) }
        query.requestId?.let { predicates += builder.equal(root.get<String>("requestId"), it) }
        builder.and(*predicates.toTypedArray())
    }

    private fun validate(
        query: AuditQueryService.Query,
        maxDays: Long = 31,
        requireTimeRange: Boolean = false,
    ) {
        require(query.page >= 1) { "页码必须大于 0" }
        require(query.page <= MAX_PAGE) { "页码不能超过 $MAX_PAGE" }
        require(query.pageSize in 1..100) { "每页数量必须在 1 到 100 之间" }
        require(!requireTimeRange || (query.occurredFrom != null && query.occurredTo != null)) {
            "审计查询必须指定开始和结束时间"
        }
        require(query.actorUserId == null || query.actorUserId > 0) { "操作者 ID 必须大于 0" }
        require(query.action == null || query.action.length <= 128) { "动作编码长度不能超过 128" }
        require(query.targetType == null || query.targetType.length <= 64) { "目标类型长度不能超过 64" }
        require(query.targetId == null || query.targetId.length <= 128) { "目标标识长度不能超过 128" }
        require(query.requestId == null || query.requestId.length <= 64) { "请求标识长度不能超过 64" }
        require(query.occurredFrom == null || query.occurredTo == null || !query.occurredTo.isBefore(query.occurredFrom)) {
            "结束时间不能早于开始时间"
        }
        require(query.occurredFrom == null || query.occurredTo == null || !query.occurredTo.isAfter(query.occurredFrom.plusDays(maxDays))) {
            "审计查询时间范围不能超过 ${maxDays} 天"
        }
    }

    private fun toData(event: AuditEvent): AuditQueryService.EventData = AuditQueryService.EventData(
        eventId = event.eventId,
        occurredAt = event.occurredAt,
        recordedAt = event.recordedAt,
        requestId = event.requestId,
        actorType = event.actorType,
        actorUserId = event.actorUserId,
        actorUsername = event.actorUsername,
        actorRole = event.actorRole,
        action = event.action,
        category = event.category,
        riskLevel = event.riskLevel,
        targetType = event.targetType,
        targetId = event.targetId,
        targetSummary = parse(event.targetSummary),
        scopeSummary = parse(event.scopeSummary),
        result = event.result,
        reasonCode = event.reasonCode,
        reason = event.reason,
        beforeData = parse(event.beforeData),
        afterData = parse(event.afterData),
        sourceSystem = event.sourceSystem,
        eventHash = event.eventHash,
    )

    private fun parse(value: String?): Any? = value?.let { runCatching { objectMapper.readTree(it) }.getOrNull() }

    private companion object {
        const val EXPORT_LIMIT = 1000
        const val MAX_PAGE = 10_000
    }
}
