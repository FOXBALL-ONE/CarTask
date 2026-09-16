package top.foxball.cartask.task

import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.support.CronTrigger
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import top.foxball.cartask.config.MaintenanceGate
import top.foxball.cartask.service.SyncScheduleChangedEvent
import top.foxball.cartask.service.SyncScheduleService
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture


@Component
@ConditionalOnProperty(name = ["spring.task.scheduling.enabled"], havingValue = "true", matchIfMissing = true)
class SyncScheduleScheduler(
    private val catalog: SyncScheduleCatalog,
    private val syncScheduleService: SyncScheduleService,
    private val maintenanceGate: MaintenanceGate,
    private val taskSchedulerProvider: ObjectProvider<TaskScheduler>,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val jobs = ConcurrentHashMap<String, ScheduledFuture<*>>()
    
    @EventListener(ApplicationReadyEvent::class)
    
    
    fun scheduleAll() {
        val scheduler = taskSchedulerProvider.ifAvailable
        if (scheduler == null) {
            log.warn("容器里没有 TaskScheduler，同步任务不会按周期自动触发")
            return
        }
        catalog.definitions.forEach { register(it.key, scheduler) }
    }
    
    
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onScheduleChanged(event: SyncScheduleChangedEvent) {
        val scheduler = taskSchedulerProvider.ifAvailable ?: return
        register(event.taskKey, scheduler)
    }
    
    @PreDestroy
    
    
    fun cancelAll() {
        jobs.values.forEach { it.cancel(false) }
        jobs.clear()
    }
    
    
    private fun register(taskKey: String, scheduler: TaskScheduler) {
        val definition = catalog.find(taskKey)
        if (definition == null) {
            log.warn("同步任务 {} 不在可调周期目录里，跳过注册", taskKey)
            return
        }
        val cron = syncScheduleService.cronFor(taskKey)
        val trigger = try {
            CronTrigger(cron, SyncScheduleCatalog.ZONE)
        } catch (exception: IllegalArgumentException) {
            log.error("同步任务 {} 的周期 {} 不合法，本次不注册：{}", taskKey, cron, exception.message)
            return
        }
        jobs.remove(taskKey)?.cancel(false)
        val scheduled = scheduler.schedule({ runGuarded(definition) }, trigger)
        if (scheduled == null) {
            log.warn("同步任务「{}」注册失败，调度器没有返回任务句柄", definition.name)
            return
        }
        jobs[taskKey] = scheduled
        log.info("同步任务「{}」已按 {} 注册，下次执行 {}", definition.name, cron, syncScheduleService.nextRunAt(taskKey))
    }
    
    
    private fun runGuarded(definition: SyncScheduleDefinition) {
        if (!maintenanceGate.enterNormalOperation()) {
            log.warn("正在生成数据备份，本次「{}」跳过，备份结束后按原周期继续", definition.name)
            return
        }
        try {
            definition.trigger()
        } finally {
            maintenanceGate.leaveNormalOperation()
        }
    }
}
