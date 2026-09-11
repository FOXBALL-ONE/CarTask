package top.foxball.cartask.task

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import top.foxball.cartask.audit.AuditRequestContext
import top.foxball.cartask.handler.ParkingAreaSyncInProgressException
import top.foxball.cartask.service.ParkingAreaSyncService

/** 从科拓同步停车区域与车场详情，维护系统可用的区域字典和停车场信息。 */
@Component
class SynAreaInfoTask(
    private val parkingAreaSyncService: ParkingAreaSyncService,
) {
    @Scheduled(cron = "\${keytop.area-sync-cron:0 0 2 * * *}", zone = "Asia/Shanghai")
    fun synAreaInfo() {
        AuditRequestContext.withRun {
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
            } catch (exception: ParkingAreaSyncInProgressException) {
                logger.warn("停车区域同步仍在执行，本次定时任务跳过")
            } catch (exception: RuntimeException) {
                logger.error("停车区域同步失败", exception)
            }
        }
    }

    private companion object {
        val logger = LoggerFactory.getLogger(SynAreaInfoTask::class.java)
    }
}
