package top.foxball.cartask.audit

/**
 * AuditEventHash 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */

import top.foxball.cartask.entity.AuditEvent
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.*


/**
 * AuditEventHash 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
object AuditEventHash {
    
    
    /**
     * calculate 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
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


