package top.foxball.cartask.task

import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.scheduling.annotation.Scheduled
import top.foxball.cartask.service.ParkingAreaSyncResult
import top.foxball.cartask.service.ParkingAreaSyncService
import kotlin.test.Test
import kotlin.test.assertEquals

class SynAreaInfoTaskTests {
    private val syncService = mock<ParkingAreaSyncService>()
    private val task = SynAreaInfoTask(syncService)

    @Test
    fun `每天上海时区凌晨两点执行`() {
        val scheduled = SynAreaInfoTask::class.java
            .getDeclaredMethod("synAreaInfo")
            .getAnnotation(Scheduled::class.java)

        assertEquals("\${keytop.area-sync-cron:0 0 2 * * *}", scheduled.cron)
        assertEquals("Asia/Shanghai", scheduled.zone)
    }

    @Test
    fun `定时任务委托停车区域同步服务`() {
        whenever(syncService.synchronize()).thenReturn(ParkingAreaSyncResult(2, 1, 1, 0))

        task.synAreaInfo()

        verify(syncService).synchronize()
    }

    @Test
    fun `同步服务失败不终止后续调度`() {
        whenever(syncService.synchronize()).thenThrow(IllegalStateException("failed"))

        task.synAreaInfo()

        verify(syncService).synchronize()
    }
}
