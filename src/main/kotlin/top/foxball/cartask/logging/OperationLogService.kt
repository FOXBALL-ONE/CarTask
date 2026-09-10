package top.foxball.cartask.logging

import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import top.foxball.cartask.entity.AuditEvent
import top.foxball.cartask.entity.OperationLog
import top.foxball.cartask.repository.OperationLogRepository
import java.time.LocalDateTime

data class OperationLogCommand(
    val requestId: String,
    val actorType: AuditEvent.ActorType,
    val actorUserId: Long?,
    val actorUsername: String,
    val actorRole: String?,
    val method: String,
    val path: String,
    val statusCode: Int,
    val result: OperationLog.Result,
    val occurredAt: LocalDateTime,
    val durationMs: Long,
    val sourceIp: String?,
    val userAgent: String?,
    val error: String?,
)

/** 将 HTTP 操作快照异步写入数据库，不阻塞业务请求。 */
@Service
class OperationLogService(
    private val repository: OperationLogRepository,
    private val meterRegistry: MeterRegistry? = null,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Async("operationLogExecutor")
    @Transactional
    fun recordAsync(command: OperationLogCommand) {
        try {
            repository.save(OperationLog().apply {
                requestId = command.requestId.take(64)
                actorType = command.actorType
                actorUserId = command.actorUserId
                actorUsername = command.actorUsername.replace(CONTROL_CHARS, " ").trim().take(128)
                    .ifBlank { "anonymous" }
                actorRole = command.actorRole?.replace(CONTROL_CHARS, " ")?.trim()?.take(64)
                method = command.method.uppercase().take(16)
                path = command.path.replace(CONTROL_CHARS, " ").take(512)
                statusCode = command.statusCode.coerceIn(100, 599)
                result = command.result
                occurredAt = command.occurredAt
                durationMs = command.durationMs.coerceAtLeast(0)
                sourceIp = command.sourceIp?.replace(CONTROL_CHARS, " ")?.take(64)
                userAgent = command.userAgent?.replace(CONTROL_CHARS, " ")?.take(512)
                error = command.error?.replace(CONTROL_CHARS, " ")?.trim()?.take(512)
            })
            meterRegistry?.counter("operation_log_events_written_total", "result", command.result.name)?.increment()
        } catch (ex: RuntimeException) {
            meterRegistry?.counter("operation_log_write_errors_total")?.increment()
            logger.error("异步写入操作日志失败，request_id={}", command.requestId, ex)
        }
    }

    private companion object {
        val CONTROL_CHARS = Regex("[\\u0000-\\u001F\\u007F]")
    }
}
