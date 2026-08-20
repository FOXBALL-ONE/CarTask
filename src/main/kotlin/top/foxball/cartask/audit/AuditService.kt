package top.foxball.cartask.audit

import top.foxball.cartask.entity.AuditEvent
import java.time.LocalDateTime

data class AuditCommand(
    val action: AuditAction,
    val targetType: String,
    val targetId: String? = null,
    val result: AuditEvent.Result = AuditEvent.Result.SUCCESS,
    val reasonCode: String? = null,
    val reason: String? = null,
    val targetSummary: Map<String, Any?>? = null,
    val scopeSummary: Map<String, Any?>? = null,
    val beforeData: Map<String, Any?>? = null,
    val afterData: Map<String, Any?>? = null,
    val occurredAt: LocalDateTime = LocalDateTime.now(),
    val sourceSystem: String? = null,
    val idempotencyKey: String? = null,
)

interface AuditService {
    fun record(command: AuditCommand): AuditEvent
}
