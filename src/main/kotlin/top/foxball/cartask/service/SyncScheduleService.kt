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

/** 某个同步任务周期发生变化，供调度器重新注册触发时间。 */
data class SyncScheduleChangedEvent(val taskKey: String)

/**
 * 同步任务周期的读写：默认值来自配置，改动落在 `sync_schedule` 表里。
 *
 * 只支持改周期，不支持停用：要停就把 cron 设成很久以后，而不是引入一个「停用」状态——
 * 那个状态一旦存在，就得在调度器、页面和执行历史里各处理一遍它。
 */
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
        /** 与配置里的默认值不同时为 true；把它改回默认值会删掉覆盖行。 */
        val customized: Boolean,
        val updatedAt: LocalDateTime?,
        val updatedBy: String?,
        val nextRunAt: LocalDateTime?,
    )

    @Transactional(readOnly = true)
            /**
             * list：查询或读取相关数据。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
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

    /**
     * 供调度器取实际生效的周期。
     *
     * 数据库里那条覆盖值解析不了时退回默认值而不是抛异常：一条手工写坏的行不该让整个
     * 应用起不来，实际周期以页面显示的为准。
     */
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

    /** 下次触发时间；cron 非法时返回 null。 */
    fun nextRunAt(taskKey: String): LocalDateTime? =
        parseOrNull(cronFor(taskKey))?.next(LocalDateTime.now(SyncScheduleCatalog.ZONE))

    /**
     * taskName：执行当前模块中的业务操作。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param taskKey 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun taskName(taskKey: String): String? = catalog.find(taskKey)?.name

    @Transactional
            /**
             * update：更新业务状态或修改相关配置。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @param taskKey 参与本次处理的输入参数。
             * @param cron 参与本次处理的输入参数。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
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
            // 改回默认值就删掉覆盖行：留一条和配置相同的记录，日后调整默认值时行为会和预期不符。
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

    /**
     * view：执行当前模块中的业务操作。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param taskKey 参与本次处理的输入参数。
     * @param taskName 参与本次处理的输入参数。
     * @param description 参与本次处理的输入参数。
     * @param defaultCron 参与本次处理的输入参数。
     * @param override 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
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

    /** Spring 的六段式解析器；五段式 Unix cron 会被拒，错误信息统一收敛成一句中文提示。 */
    private fun parseOrNull(cron: String): CronExpression? =
        runCatching { CronExpression.parse(cron.trim()) }.getOrNull()

    private companion object {
        const val MAX_CRON_LENGTH = 128

        /** 任务键在目录里一定找得到；真找不到说明调用方传错了，用一个不会触发的表达式兜底。 */
        const val DEFAULT_FALLBACK_CRON = "0 0 0 1 1 *"
    }
}
