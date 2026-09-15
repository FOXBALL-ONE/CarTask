package top.foxball.cartask.audit

import top.foxball.cartask.entity.AuditEvent
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.*

/** 写入和校验共用的审计事件摘要算法。 */
object AuditEventHash {
    /**
     * calculate：转换、构建或格式化数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param event 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
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
