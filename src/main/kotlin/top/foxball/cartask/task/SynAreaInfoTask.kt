package top.foxball.cartask.task

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import top.foxball.cartask.audit.AuditRequestContext
import top.foxball.cartask.entity.SyncTaskRun
import top.foxball.cartask.handler.ParkingAreaSyncInProgressException
import top.foxball.cartask.service.ParkingAreaSyncResult
import top.foxball.cartask.service.ParkingAreaSyncService
import top.foxball.cartask.service.SyncTaskHistoryService
import top.foxball.cartask.service.SyncTaskRunCommand
import top.foxball.cartask.service.SyncTaskProgressService
import java.time.LocalDateTime

/** 从科拓同步停车区域与车场详情，维护系统可用的区域字典和停车场信息。 */
@Component
class SynAreaInfoTask(
    private val parkingAreaSyncService: ParkingAreaSyncService,
    private val syncTaskHistoryService: SyncTaskHistoryService,
    private val syncTaskProgressService: SyncTaskProgressService = SyncTaskProgressService(),
) {
    @Scheduled(cron = "\${keytop.area-sync-cron:0 0 2 * * *}", zone = "Asia/Shanghai")
    fun synAreaInfo() {
        AuditRequestContext.withRun {
            try {
                synchronize(SyncTaskRun.Trigger.SCHEDULED)
            } catch (exception: ParkingAreaSyncInProgressException) {
                logger.warn("停车区域同步仍在执行，本次定时任务跳过")
            } catch (exception: RuntimeException) {
                logger.error("停车区域同步失败", exception)
            }
        }
    }

    /** 手动执行一次停车区域同步，幂等写入本地区域字典。 */
    fun synchronize(): ParkingAreaSyncResult = synchronize(SyncTaskRun.Trigger.MANUAL)

    private fun synchronize(trigger: SyncTaskRun.Trigger): ParkingAreaSyncResult {
        val startedAt = LocalDateTime.now()
        syncTaskProgressService.start(TASK_KEY, TASK_NAME, startedAt)
        try {
            val result = parkingAreaSyncService.synchronize()
            if (result.lotName != null) {
                logger.info(
                    "停车场详情已保存：{}，总车位 {}",
                    result.lotName,
                    result.totalPlaceCount ?: "未知",
                )
            }
            if (result.receivedCount == 0) {
                logger.warn("Keytop 停车区域接口返回为空，本次不更新区域字典")
            } else {
                logger.info(
                    "停车区域同步完成：接收 {} 个，新增 {} 个，更新 {} 个，未变化 {} 个",
                    result.receivedCount,
                    result.createdCount,
                    result.updatedCount,
                    result.unchangedCount,
                )
            }
            recordHistory(trigger, SyncTaskRun.Status.SUCCESS, startedAt, result, null)
            return result
        } catch (exception: ParkingAreaSyncInProgressException) {
            throw exception
        } catch (exception: RuntimeException) {
            recordHistory(trigger, SyncTaskRun.Status.FAILED, startedAt, null, exception)
            throw exception
        } finally {
            syncTaskProgressService.finish(TASK_KEY, startedAt)
        }
    }

    private fun recordHistory(
        trigger: SyncTaskRun.Trigger,
        status: SyncTaskRun.Status,
        startedAt: LocalDateTime,
        result: ParkingAreaSyncResult?,
        exception: RuntimeException?,
    ) {
        try {
            syncTaskHistoryService.record(
                SyncTaskRunCommand(
                    taskKey = TASK_KEY,
                    taskName = TASK_NAME,
                    trigger = trigger,
                    status = status,
                    startedAt = startedAt,
                    finishedAt = LocalDateTime.now(),
                    processedCount = result?.receivedCount,
                    summary = result?.let {
                        "接收 ${it.receivedCount} 个，新增 ${it.createdCount} 个，更新 ${it.updatedCount} 个，未变化 ${it.unchangedCount} 个"
                    },
                    error = exception?.message?.take(2048) ?: exception?.javaClass?.simpleName,
                ),
            )
        } catch (historyException: RuntimeException) {
            logger.warn("写入停车区域同步执行历史失败", historyException)
        }
    }

    private companion object {
        const val TASK_KEY = "parking_area.sync"
        const val TASK_NAME = "停车区域同步"
        val logger = LoggerFactory.getLogger(SynAreaInfoTask::class.java)
    }
}
