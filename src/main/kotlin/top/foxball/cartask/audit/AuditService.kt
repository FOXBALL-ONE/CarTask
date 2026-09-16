package top.foxball.cartask.audit

/**
 * AuditService 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */

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

/**
 * AuditService 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
interface AuditService {
    
    
    /**
     * record 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun record(command: AuditCommand): AuditEvent
}


