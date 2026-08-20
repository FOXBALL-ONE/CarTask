package top.foxball.cartask.audit

import top.foxball.cartask.entity.AuditEvent
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.HexFormat

/** 写入和校验共用的审计事件摘要算法。 */
object AuditEventHash {
    fun calculate(event: AuditEvent): String {
        val canonical = listOf(
            event.eventId,
            event.occurredAt,
            event.recordedAt,
            event.requestId,
            event.actorType,
            event.actorUserId,
            event.actorUsername,
            event.actorRole,
            event.authorities,
            event.action,
            event.category,
            event.riskLevel,
            event.targetType,
            event.targetId,
            event.targetSummary,
            event.scopeSummary,
            event.result,
            event.reasonCode,
            event.reason,
            event.beforeData,
            event.afterData,
            event.sourceIp,
            event.userAgent,
            event.sourceSystem,
            event.partitionKey,
            event.sequenceNo,
            event.previousHash,
            event.idempotencyKey,
        ).joinToString("\u001f") { it?.toString() ?: "" }
        return HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(StandardCharsets.UTF_8)),
        )
    }
}
