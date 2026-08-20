package top.foxball.cartask.service

import top.foxball.cartask.entity.AuditEvent
import java.time.LocalDateTime
import java.util.UUID

interface AuditQueryService {
    data class Query(
        val occurredFrom: LocalDateTime? = null,
        val occurredTo: LocalDateTime? = null,
        val actorUserId: Long? = null,
        val action: String? = null,
        val targetType: String? = null,
        val targetId: String? = null,
        val result: AuditEvent.Result? = null,
        val riskLevel: AuditEvent.RiskLevel? = null,
        val requestId: String? = null,
        val page: Int = 1,
        val pageSize: Int = 20,
    )

    data class EventData(
        val eventId: UUID,
        val occurredAt: LocalDateTime,
        val recordedAt: LocalDateTime,
        val requestId: String?,
        val actorType: AuditEvent.ActorType,
        val actorUserId: Long?,
        val actorUsername: String,
        val actorRole: String?,
        val action: String,
        val category: AuditEvent.Category,
        val riskLevel: AuditEvent.RiskLevel,
        val targetType: String,
        val targetId: String?,
        val targetSummary: Any?,
        val scopeSummary: Any?,
        val result: AuditEvent.Result,
        val reasonCode: String?,
        val reason: String?,
        val beforeData: Any?,
        val afterData: Any?,
        val sourceSystem: String,
        val eventHash: String,
    )

    data class PageData(
        val events: List<EventData>,
        val page: Int,
        val pageSize: Int,
        val total: Long,
    )

    data class VerificationData(
        val partitionKey: String,
        val valid: Boolean,
        val eventCount: Int,
        val firstSequence: Long?,
        val lastSequence: Long?,
        val message: String,
    )

    fun list(query: Query): PageData
    fun get(eventId: UUID): EventData
    fun export(query: Query): List<EventData>
    fun verify(partitionKey: String): VerificationData
}
