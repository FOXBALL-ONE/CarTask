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
    /**
     * record：执行当前模块中的业务操作。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param command 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun record(command: AuditCommand): AuditEvent
}
