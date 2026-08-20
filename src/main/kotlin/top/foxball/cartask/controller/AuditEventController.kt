package top.foxball.cartask.controller

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import top.foxball.cartask.entity.AuditEvent
import top.foxball.cartask.service.AuditQueryService
import top.foxball.cartask.shared.Response
import top.foxball.cartask.shared.ResponseBuilder
import java.time.LocalDateTime
import java.util.UUID

@RestController
@RequestMapping("/admin/api/audit-events")
class AuditEventController(
    private val service: AuditQueryService,
    private val responseBuilder: ResponseBuilder,
) {
    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') and hasAuthority('audit:read')")
    fun list(
        @RequestParam(name = "occurred_from", required = false) occurredFrom: LocalDateTime?,
        @RequestParam(name = "occurred_to", required = false) occurredTo: LocalDateTime?,
        @RequestParam(name = "actor_user_id", required = false) actorUserId: Long?,
        @RequestParam(required = false) action: String?,
        @RequestParam(name = "target_type", required = false) targetType: String?,
        @RequestParam(name = "target_id", required = false) targetId: String?,
        @RequestParam(required = false) result: AuditEvent.Result?,
        @RequestParam(name = "risk_level", required = false) riskLevel: AuditEvent.RiskLevel?,
        @RequestParam(name = "request_id", required = false) requestId: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(name = "page_size", defaultValue = "20") pageSize: Int,
    ): ResponseEntity<Response> {
        data class EventData(
            @param:JsonProperty("event_id") val eventId: UUID,
            @param:JsonProperty("occurred_at") val occurredAt: LocalDateTime,
            @param:JsonProperty("recorded_at") val recordedAt: LocalDateTime,
            @param:JsonProperty("request_id") val requestId: String?,
            @param:JsonProperty("actor_type") val actorType: AuditEvent.ActorType,
            @param:JsonProperty("actor_user_id") val actorUserId: Long?,
            @param:JsonProperty("actor_username") val actorUsername: String,
            @param:JsonProperty("actor_role") val actorRole: String?,
            val action: String,
            val category: AuditEvent.Category,
            @param:JsonProperty("risk_level") val riskLevel: AuditEvent.RiskLevel,
            @param:JsonProperty("target_type") val targetType: String,
            @param:JsonProperty("target_id") val targetId: String?,
            @param:JsonProperty("target_summary") val targetSummary: Any?,
            @param:JsonProperty("scope_summary") val scopeSummary: Any?,
            val result: AuditEvent.Result,
            @param:JsonProperty("reason_code") val reasonCode: String?,
            val reason: String?,
            @param:JsonProperty("before_data") val beforeData: Any?,
            @param:JsonProperty("after_data") val afterData: Any?,
            @param:JsonProperty("source_system") val sourceSystem: String,
            @param:JsonProperty("event_hash") val eventHash: String,
        )
        data class Response(
            val events: List<EventData>,
            val page: Int,
            @param:JsonProperty("page_size") val pageSize: Int,
            val total: Long,
        )
        val resultData = service.list(AuditQueryService.Query(occurredFrom, occurredTo, actorUserId, action, targetType, targetId, result, riskLevel, requestId, page, pageSize))
        val rs = Response(resultData.events.map {
            EventData(
                it.eventId, it.occurredAt, it.recordedAt, it.requestId, it.actorType, it.actorUserId,
                it.actorUsername, it.actorRole, it.action, it.category, it.riskLevel,
                it.targetType, it.targetId, it.targetSummary, it.scopeSummary, it.result, it.reasonCode,
                it.reason, it.beforeData, it.afterData, it.sourceSystem, it.eventHash,
            )
        }, resultData.page, resultData.pageSize, resultData.total)
        return responseBuilder.ok().data(rs).build()
    }

    @GetMapping("/{event_id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') and hasAuthority('audit:read')")
    fun get(@PathVariable("event_id") eventId: UUID): ResponseEntity<Response> {
        data class Response(
            @param:JsonProperty("event_id") val eventId: UUID,
            @param:JsonProperty("occurred_at") val occurredAt: LocalDateTime,
            @param:JsonProperty("recorded_at") val recordedAt: LocalDateTime,
            @param:JsonProperty("request_id") val requestId: String?,
            @param:JsonProperty("actor_type") val actorType: AuditEvent.ActorType,
            @param:JsonProperty("actor_user_id") val actorUserId: Long?,
            @param:JsonProperty("actor_username") val actorUsername: String,
            @param:JsonProperty("actor_role") val actorRole: String?,
            val action: String,
            val category: AuditEvent.Category,
            @param:JsonProperty("risk_level") val riskLevel: AuditEvent.RiskLevel,
            @param:JsonProperty("target_type") val targetType: String,
            @param:JsonProperty("target_id") val targetId: String?,
            @param:JsonProperty("target_summary") val targetSummary: Any?,
            @param:JsonProperty("scope_summary") val scopeSummary: Any?,
            val result: AuditEvent.Result,
            @param:JsonProperty("reason_code") val reasonCode: String?,
            val reason: String?,
            @param:JsonProperty("before_data") val beforeData: Any?,
            @param:JsonProperty("after_data") val afterData: Any?,
            @param:JsonProperty("source_system") val sourceSystem: String,
            @param:JsonProperty("event_hash") val eventHash: String,
        )
        val event = service.get(eventId)
        val rs = Response(
            event.eventId, event.occurredAt, event.recordedAt, event.requestId, event.actorType,
            event.actorUserId, event.actorUsername, event.actorRole, event.action,
            event.category, event.riskLevel, event.targetType, event.targetId, event.targetSummary,
            event.scopeSummary, event.result, event.reasonCode, event.reason, event.beforeData,
            event.afterData, event.sourceSystem, event.eventHash,
        )
        return responseBuilder.ok().data(rs).build()
    }

    @PostMapping("/export")
    @PreAuthorize("hasRole('SUPER_ADMIN') and hasAuthority('audit:export')")
    fun export(
        @RequestParam(name = "occurred_from", required = false) occurredFrom: LocalDateTime?,
        @RequestParam(name = "occurred_to", required = false) occurredTo: LocalDateTime?,
        @RequestParam(name = "actor_user_id", required = false) actorUserId: Long?,
        @RequestParam(required = false) action: String?,
        @RequestParam(name = "target_type", required = false) targetType: String?,
        @RequestParam(name = "target_id", required = false) targetId: String?,
        @RequestParam(required = false) result: AuditEvent.Result?,
        @RequestParam(name = "risk_level", required = false) riskLevel: AuditEvent.RiskLevel?,
        @RequestParam(name = "request_id", required = false) requestId: String?,
    ): ResponseEntity<Response> {
        data class EventData(
            @param:JsonProperty("event_id") val eventId: UUID,
            @param:JsonProperty("occurred_at") val occurredAt: LocalDateTime,
            @param:JsonProperty("recorded_at") val recordedAt: LocalDateTime,
            @param:JsonProperty("request_id") val requestId: String?,
            @param:JsonProperty("actor_type") val actorType: AuditEvent.ActorType,
            @param:JsonProperty("actor_user_id") val actorUserId: Long?,
            @param:JsonProperty("actor_username") val actorUsername: String,
            @param:JsonProperty("actor_role") val actorRole: String?,
            val action: String,
            val category: AuditEvent.Category,
            @param:JsonProperty("risk_level") val riskLevel: AuditEvent.RiskLevel,
            @param:JsonProperty("target_type") val targetType: String,
            @param:JsonProperty("target_id") val targetId: String?,
            @param:JsonProperty("target_summary") val targetSummary: Any?,
            @param:JsonProperty("scope_summary") val scopeSummary: Any?,
            val result: AuditEvent.Result,
            @param:JsonProperty("reason_code") val reasonCode: String?,
            val reason: String?,
            @param:JsonProperty("before_data") val beforeData: Any?,
            @param:JsonProperty("after_data") val afterData: Any?,
            @param:JsonProperty("source_system") val sourceSystem: String,
            @param:JsonProperty("event_hash") val eventHash: String,
        )
        data class Response(
            val events: List<EventData>,
            @param:JsonProperty("record_count") val recordCount: Int,
        )
        val events = service.export(AuditQueryService.Query(occurredFrom, occurredTo, actorUserId, action, targetType, targetId, result, riskLevel, requestId, pageSize = 100))
        val rs = Response(events.map {
            EventData(
                it.eventId, it.occurredAt, it.recordedAt, it.requestId, it.actorType, it.actorUserId,
                it.actorUsername, it.actorRole, it.action, it.category, it.riskLevel,
                it.targetType, it.targetId, it.targetSummary, it.scopeSummary, it.result, it.reasonCode,
                it.reason, it.beforeData, it.afterData, it.sourceSystem, it.eventHash,
            )
        }, events.size)
        return responseBuilder.ok().data(rs).build()
    }

    @GetMapping("/verify")
    @PreAuthorize("hasRole('SUPER_ADMIN') and hasAuthority('audit:verify')")
    fun verify(@RequestParam(name = "partition_key") partitionKey: String): ResponseEntity<Response> {
        data class Response(
            @param:JsonProperty("partition_key") val partitionKey: String,
            val valid: Boolean,
            @param:JsonProperty("event_count") val eventCount: Int,
            @param:JsonProperty("first_sequence") val firstSequence: Long?,
            @param:JsonProperty("last_sequence") val lastSequence: Long?,
            val message: String,
        )
        val result = service.verify(partitionKey)
        val rs = Response(result.partitionKey, result.valid, result.eventCount, result.firstSequence, result.lastSequence, result.message)
        return responseBuilder.ok().data(rs).build()
    }
}
