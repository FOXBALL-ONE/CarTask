package top.foxball.cartask.service

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import top.foxball.cartask.audit.AuditRequestContext
import top.foxball.cartask.authentication.CurrentUserPrincipal
import top.foxball.cartask.config.SyncProperties
import top.foxball.cartask.entity.SyncTaskRun
import top.foxball.cartask.repository.SyncTaskRunRepository
import java.time.Duration
import java.time.LocalDateTime

data class SyncTaskRunCommand(
    val taskKey: String,
    val taskName: String,
    val trigger: SyncTaskRun.Trigger,
    val status: SyncTaskRun.Status,
    val startedAt: LocalDateTime,
    val finishedAt: LocalDateTime,
    val dataStartTime: LocalDateTime? = null,
    val dataEndTime: LocalDateTime? = null,
    val processedCount: Int? = null,
    val localPhotoCount: Int? = null,
    val failedPhotoCount: Int? = null,
    val summary: String? = null,
    val error: String? = null,
)

/** 写入同步执行历史，并按任务标识保留配置数量的最近记录。 */
@Service
class SyncTaskHistoryService(
    private val repository: SyncTaskRunRepository,
    private val properties: SyncProperties,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun record(command: SyncTaskRunCommand): SyncTaskRun {
        val taskKey = command.taskKey.trim().take(128)
        val taskName = command.taskName.trim().take(128)
        require(taskKey.isNotEmpty()) { "同步任务标识不能为空" }
        require(taskName.isNotEmpty()) { "同步任务名称不能为空" }

        val request = AuditRequestContext.current()
        val principal = SecurityContextHolder.getContext().authentication?.principal as? CurrentUserPrincipal
        val sourceSystem = request?.sourceSystem?.trim()?.uppercase()?.takeIf(String::isNotEmpty) ?: "SYSTEM"
        val actorUsername = principal?.username ?: if (sourceSystem == "SYSTEM") "SYSTEM" else "anonymous"
        val run = SyncTaskRun().apply {
            this.taskKey = taskKey
            this.taskName = taskName
            trigger = command.trigger
            status = command.status
            requestId = request?.requestId?.take(64)
            this.sourceSystem = sourceSystem.take(32)
            actorUserId = principal?.userId
            this.actorUsername = actorUsername.take(128)
            actorRole = principal?.role?.take(64)
            startedAt = command.startedAt
            finishedAt = command.finishedAt
            durationMs = Duration.between(command.startedAt, command.finishedAt).toMillis().coerceAtLeast(0)
            dataStartTime = command.dataStartTime
            dataEndTime = command.dataEndTime
            processedCount = command.processedCount
            localPhotoCount = command.localPhotoCount
            failedPhotoCount = command.failedPhotoCount
            summary = sanitize(command.summary, 2048)
            error = sanitize(command.error, 2048)
        }
        val saved = repository.save(run)
        val expired = repository.findAllByTaskKeyOrderByStartedAtDescIdDesc(taskKey)
            .drop(properties.taskHistoryLimit)
        if (expired.isNotEmpty()) {
            repository.deleteAllInBatch(expired)
        }
        return saved
    }

    private fun sanitize(value: String?, limit: Int): String? = value
        ?.replace(Regex("[\\u0000-\\u001F\\u007F]"), " ")
        ?.trim()
        ?.take(limit)
        ?.takeIf(String::isNotEmpty)
}
