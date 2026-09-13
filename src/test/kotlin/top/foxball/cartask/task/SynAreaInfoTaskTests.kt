package top.foxball.cartask.task

import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import top.foxball.cartask.entity.SyncTaskRun
import top.foxball.cartask.service.ParkingAreaSyncResult
import top.foxball.cartask.service.ParkingAreaSyncService
import top.foxball.cartask.service.SyncTaskHistoryService
import top.foxball.cartask.service.SyncTaskRunCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SynAreaInfoTaskTests {
    private val syncService = mock<ParkingAreaSyncService>()
    private val historyService = mock<SyncTaskHistoryService>()
    private val task = SynAreaInfoTask(syncService, historyService)

    @Test
    fun `定时任务委托停车区域同步服务`() {
        whenever(syncService.synchronize()).thenReturn(ParkingAreaSyncResult(2, 1, 1, 0))

        task.synAreaInfo()

        verify(syncService).synchronize()
        val command = argumentCaptor<SyncTaskRunCommand>()
        verify(historyService).record(command.capture())
        assertEquals(SyncTaskRun.Trigger.SCHEDULED, command.firstValue.trigger)
        assertEquals(SyncTaskRun.Status.SUCCESS, command.firstValue.status)
        assertEquals("parking_area.sync", command.firstValue.taskKey)
    }

    @Test
    fun `同步服务失败不终止后续调度并记录失败历史`() {
        whenever(syncService.synchronize()).thenThrow(IllegalStateException("failed"))

        task.synAreaInfo()

        verify(syncService).synchronize()
        val command = argumentCaptor<SyncTaskRunCommand>()
        verify(historyService).record(command.capture())
        assertEquals(SyncTaskRun.Trigger.SCHEDULED, command.firstValue.trigger)
        assertEquals(SyncTaskRun.Status.FAILED, command.firstValue.status)
        assertEquals("failed", command.firstValue.error)
    }

    @Test
    fun `手动触发记录手动执行历史并返回统计`() {
        whenever(syncService.synchronize()).thenReturn(ParkingAreaSyncResult(5, 2, 1, 2))

        val result = task.synchronize()

        assertEquals(5, result.receivedCount)
        val command = argumentCaptor<SyncTaskRunCommand>()
        verify(historyService).record(command.capture())
        assertEquals(SyncTaskRun.Trigger.MANUAL, command.firstValue.trigger)
        assertEquals(SyncTaskRun.Status.SUCCESS, command.firstValue.status)
        assertEquals(5, command.firstValue.processedCount)
        assertEquals("接收 5 个，新增 2 个，更新 1 个，未变化 2 个", command.firstValue.summary)
        assertNull(command.firstValue.error)
    }
}
