package top.foxball.cartask.task

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import top.foxball.cartask.service.PlateKeytopSyncService
import top.foxball.cartask.service.SyncTaskHistoryService
import top.foxball.cartask.service.SyncTaskProgressService
import top.foxball.cartask.service.SyncTaskRunCommand
import top.foxball.cartask.entity.SyncTaskRun
import java.time.LocalDateTime

@Component
class PlateKeytopSyncTask(
    private val plateKeytopSyncService: PlateKeytopSyncService,
    private val syncTaskHistoryService: SyncTaskHistoryService,
    private val syncTaskProgressService: SyncTaskProgressService,
) {
    @Scheduled(cron = "\${keytop.plate-sync-cron:*/30 * * * * *}")
    fun synchronize() {
        run(SyncTaskRun.Trigger.SCHEDULED)
    }

    fun synchronizeManually(): PlateKeytopSyncResult = run(SyncTaskRun.Trigger.MANUAL)

    @Scheduled(cron = "\${keytop.plate-reconcile-cron:0 0 * * * *}")
    fun reconcile() {
        plateKeytopSyncService.reconcile()
    }

    private fun run(trigger: SyncTaskRun.Trigger): PlateKeytopSyncResult {
        val startedAt = LocalDateTime.now()
        syncTaskProgressService.start(TASK_KEY, TASK_NAME, startedAt)
        return try {
            val processedCount = plateKeytopSyncService.processBatch()
            val result = PlateKeytopSyncResult(processedCount, LocalDateTime.now())
            syncTaskHistoryService.record(
                SyncTaskRunCommand(
                    taskKey = TASK_KEY,
                    taskName = TASK_NAME,
                    trigger = trigger,
                    status = SyncTaskRun.Status.SUCCESS,
                    startedAt = startedAt,
                    finishedAt = result.executedAt,
                    processedCount = processedCount,
                    summary = "处理 $processedCount 条车牌月卡同步任务",
                ),
            )
            result
        } catch (exception: RuntimeException) {
            val finishedAt = LocalDateTime.now()
            syncTaskHistoryService.record(
                SyncTaskRunCommand(
                    taskKey = TASK_KEY,
                    taskName = TASK_NAME,
                    trigger = trigger,
                    status = SyncTaskRun.Status.FAILED,
                    startedAt = startedAt,
                    finishedAt = finishedAt,
                    error = exception.message ?: exception.javaClass.simpleName,
                ),
            )
            throw exception
        } finally {
            syncTaskProgressService.finish(TASK_KEY, startedAt)
        }
    }

    data class PlateKeytopSyncResult(
        val processedCount: Int,
        val executedAt: LocalDateTime,
    )

    companion object {
        const val TASK_KEY = "plate.keytop.sync"
        const val TASK_NAME = "车辆月卡同步"
    }
}
