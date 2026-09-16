package top.foxball.cartask.service

import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.support.CronExpression
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import top.foxball.cartask.audit.AuditAction
import top.foxball.cartask.audit.AuditCommand
import top.foxball.cartask.audit.AuditService
import top.foxball.cartask.authentication.CurrentUserPrincipal
import top.foxball.cartask.entity.SyncSchedule
import top.foxball.cartask.handler.ParamErrorException
import top.foxball.cartask.handler.ResourceNotFoundException
import top.foxball.cartask.repository.SyncScheduleRepository
import top.foxball.cartask.task.SyncScheduleCatalog
import java.time.LocalDateTime


data class SyncScheduleChangedEvent(val taskKey: String)


@Service
class SyncScheduleService(
    private val catalog: SyncScheduleCatalog,
    private val repository: SyncScheduleRepository,
    private val auditService: AuditService,
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    
    data class ScheduleView(
        val taskKey: String,
        val taskName: String,
        val description: String,
        val cron: String,
        val defaultCron: String,
        
        val customized: Boolean,
        val updatedAt: LocalDateTime?,
        val updatedBy: String?,
        val nextRunAt: LocalDateTime?,
    )
    
    @Transactional(readOnly = true)
    
    
    fun list(): List<ScheduleView> {
        val overrides = repository.findAll().associateBy { it.taskKey }
        return catalog.definitions.map { definition ->
            view(
                definition.key,
                definition.name,
                definition.description,
                definition.defaultCron,
                overrides[definition.key]
            )
        }
    }
    
    
    fun cronFor(taskKey: String): String {
        val definition = catalog.find(taskKey) ?: return DEFAULT_FALLBACK_CRON
        val override = repository.findById(taskKey).orElse(null) ?: return definition.defaultCron
        val expression = parseOrNull(override.cronExpression)
        if (expression == null) {
            log.error(
                "同步任务 {} 的周期 {} 无法解析，本次改用默认值 {}",
                taskKey,
                override.cronExpression,
                definition.defaultCron
            )
            return definition.defaultCron
        }
        return override.cronExpression.trim()
    }
    
    
    fun nextRunAt(taskKey: String): LocalDateTime? =
        parseOrNull(cronFor(taskKey))?.next(LocalDateTime.now(SyncScheduleCatalog.ZONE))
    
    
    fun taskName(taskKey: String): String? = catalog.find(taskKey)?.name
    
    @Transactional
    
    
    fun update(taskKey: String, cron: String): ScheduleView {
        val definition = catalog.find(taskKey) ?: throw ResourceNotFoundException("同步任务不存在")
        val normalized = cron.trim()
        if (normalized.isEmpty()) throw ParamErrorException("cron 表达式不能为空")
        if (normalized.length > MAX_CRON_LENGTH) throw ParamErrorException("cron 表达式过长")
        val expression = parseOrNull(normalized)
            ?: throw ParamErrorException("cron 表达式不合法，请使用六段式「秒 分 时 日 月 周」，例如 0 */5 * * * *")
        if (expression.next(LocalDateTime.now(SyncScheduleCatalog.ZONE)) == null) {
            throw ParamErrorException("cron 表达式不会触发任何一次执行，请检查日期或星期字段")
        }
        
        val existing = repository.findById(taskKey).orElse(null)
        val previous = existing?.cronExpression ?: definition.defaultCron
        
        val principal = SecurityContextHolder.getContext().authentication?.principal as? CurrentUserPrincipal
        if (normalized == definition.defaultCron) {
            if (existing != null) repository.delete(existing)
        } else {
            val saved = existing ?: SyncSchedule().apply { this.taskKey = definition.key }
            saved.cronExpression = normalized
            saved.updatedAt = LocalDateTime.now()
            saved.updatedByUserId = principal?.userId
            saved.updatedByUsername = principal?.username?.take(128)
            repository.save(saved)
        }
        
        auditService.record(
            AuditCommand(
                AuditAction.SYNC_SCHEDULE_CHANGED,
                "sync_schedule",
                taskKey,
                targetSummary = mapOf(
                    "task" to definition.name,
                    "cron" to normalized,
                    "previous_cron" to previous,
                ),
            ),
        )
        eventPublisher.publishEvent(SyncScheduleChangedEvent(taskKey))
        
        val current = repository.findById(taskKey).orElse(null)
        return view(taskKey, definition.name, definition.description, definition.defaultCron, current)
    }
    
    
    private fun view(
        taskKey: String,
        taskName: String,
        description: String,
        defaultCron: String,
        override: SyncSchedule?,
    ): ScheduleView {
        val cron = override?.cronExpression?.trim()?.takeIf { parseOrNull(it) != null } ?: defaultCron
        return ScheduleView(
            taskKey = taskKey,
            taskName = taskName,
            description = description,
            cron = cron,
            defaultCron = defaultCron,
            customized = override != null && cron != defaultCron,
            updatedAt = override?.updatedAt,
            updatedBy = override?.updatedByUsername,
            nextRunAt = parseOrNull(cron)?.next(LocalDateTime.now(SyncScheduleCatalog.ZONE)),
        )
    }
    
    
    private fun parseOrNull(cron: String): CronExpression? =
        runCatching { CronExpression.parse(cron.trim()) }.getOrNull()
    
    private companion object {
        const val MAX_CRON_LENGTH = 128
        
        
        const val DEFAULT_FALLBACK_CRON = "0 0 0 1 1 *"
    }
}
