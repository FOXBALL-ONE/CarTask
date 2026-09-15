package top.foxball.cartask.task

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import top.foxball.cartask.audit.AuditRequestContext
import top.foxball.cartask.entity.SyncTaskRun
import top.foxball.cartask.handler.ParkingAreaSyncInProgressException
import top.foxball.cartask.service.*
import java.time.LocalDateTime

/** 从科拓同步停车区域与车场详情，维护系统可用的区域字典和停车场信息。 */
@Component
class SynAreaInfoTask(
    private val parkingAreaSyncService: ParkingAreaSyncService,
    private val syncTaskHistoryService: SyncTaskHistoryService,
    private val syncTaskProgressService: SyncTaskProgressService = SyncTaskProgressService(),
) {
    /**
     * 定时入口。周期由 [SyncScheduleCatalog] 注册、[SyncScheduleScheduler] 按 cron 触发，
     * 不再用 @Scheduled 固定：周期要能在页面上改。
     */
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

    /**
     * synchronize：执行数据同步、探测或文件处理。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param trigger 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
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

    /**
     * recordHistory：执行当前模块中的业务操作。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param trigger 参与本次处理的输入参数。
     * @param status 参与本次处理的输入参数。
     * @param startedAt 参与本次处理的输入参数。
     * @param result 参与本次处理的输入参数。
     * @param exception 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
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

    companion object {
        const val TASK_KEY = "parking_area.sync"
        const val TASK_NAME = "停车区域同步"
        val logger = LoggerFactory.getLogger(SynAreaInfoTask::class.java)
    }
}
