package top.foxball.cartask.task

import java.time.Duration
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.scheduling.annotation.Scheduled
import top.foxball.cartask.entity.CarMasterInfo
import top.foxball.cartask.entity.SyncTaskRun
import top.foxball.cartask.repository.AccessRecordRepository
import top.foxball.cartask.service.CarMasterInfoService
import top.foxball.cartask.service.SyncTaskHistoryService
import top.foxball.cartask.service.SyncTaskRunCommand
import top.foxball.cartask.service.UserService
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SynAccountGenerateTaskTests {
    private val userService = mock<UserService>()
    private val carMasterInfoService = mock<CarMasterInfoService>()
    private val accessRecordRepository = mock<AccessRecordRepository>()
    private val historyService = mock<SyncTaskHistoryService>()
    private val task = SynAccountGenerateTask(userService, carMasterInfoService, accessRecordRepository, historyService)

    private fun carMaster(id: Long, name: String, phone: String?, vararg plates: String): CarMasterInfo =
        CarMasterInfo().apply {
            this.id = id
            carMasterName = name
            carMasterPhone = phone
            plates.forEach { plate ->
                cards.add(CarMasterInfo.CarCardItem().apply { carNumber = plate })
            }
        }

    @Test
    fun `每天上海时区凌晨三点执行`() {
        val scheduled = SynAccountGenerateTask::class.java
            .getDeclaredMethod("synAccountGenerate")
            .getAnnotation(Scheduled::class.java)

        assertEquals("\${app.account-generate-cron:0 0 3 * * *}", scheduled.cron)
        assertEquals("Asia/Shanghai", scheduled.zone)
    }

    @Test
    fun `为近三十天活跃车牌的业主创建平台账户`() {
        whenever(accessRecordRepository.findDistinctCarNumbersSince(any())).thenReturn(listOf(" 沪A12345 "))
        whenever(carMasterInfoService.getAllList()).thenReturn(
            listOf(carMaster(1L, "张三", " 13800138000 ", "沪A12345", "沪B00000")),
        )
        whenever(userService.findExistingUsernames(setOf("13800138000"))).thenReturn(emptySet())
        val command = argumentCaptor<UserService.CreateCommand>()
        val startTimeCaptor = argumentCaptor<LocalDateTime>()

        task.generate()

        verify(accessRecordRepository).findDistinctCarNumbersSince(startTimeCaptor.capture())
        val days = Duration.between(startTimeCaptor.firstValue, LocalDateTime.now()).toDays()
        assertTrue(days in 29..31, "查询范围应回溯约 30 天，实际 ${days} 天")
        verify(userService).create(command.capture())
        assertEquals("13800138000", command.firstValue.username)
        assertEquals("13800138000", command.firstValue.phone)
        assertEquals("13800138000@auto.local", command.firstValue.email)
        assertEquals("Fqjg20221022", command.firstValue.credential)
        assertEquals("张三", command.firstValue.nickName)
        assertEquals(229L, command.firstValue.departmentId)
    }

    @Test
    fun `跳过无主档无手机号已有账户的车牌`() {
        whenever(accessRecordRepository.findDistinctCarNumbersSince(any()))
            .thenReturn(listOf("沪A12345", "沪B12345", "沪C12345"))
        whenever(carMasterInfoService.getAllList()).thenReturn(
            listOf(
                carMaster(1L, "张三", "13800138000", "沪A12345"),
                carMaster(2L, "李四", " ", "沪B12345"),
            ),
        )
        whenever(userService.findExistingUsernames(setOf("13800138000"))).thenReturn(setOf("13800138000"))

        val result = task.generate()

        verify(userService, never()).create(any())
        assertEquals(0, result.createdCount)
        assertEquals(3, result.skippedCount)
    }

    @Test
    fun `多个车牌指向同一业主时只处理一次且失败不中断后续`() {
        whenever(accessRecordRepository.findDistinctCarNumbersSince(any()))
            .thenReturn(listOf("plate-a", "plate-b", "plate-c"))
        whenever(carMasterInfoService.getAllList()).thenReturn(
            listOf(
                carMaster(1L, "张三", "phone-shared", "plate-a"),
                carMaster(2L, "李四", "phone-shared", "plate-b"),
                carMaster(3L, "王五", "phone-success", "plate-c"),
            ),
        )
        whenever(
            userService.findExistingUsernames(setOf("phone-shared", "phone-success")),
        ).thenReturn(emptySet())
        whenever(userService.create(any())).thenThrow(IllegalStateException("创建失败"))
        val command = argumentCaptor<UserService.CreateCommand>()

        val result = task.generate()

        verify(userService).findExistingUsernames(setOf("phone-shared", "phone-success"))
        verify(userService, org.mockito.kotlin.times(2)).create(command.capture())
        assertEquals("phone-shared", command.allValues[0].username)
        assertEquals("phone-success", command.allValues[1].username)
        assertEquals(0, result.createdCount)
        assertEquals(2, result.failedCount)
        assertEquals(1, result.skippedCount)
    }

    @Test
    fun `近三十天没有进出记录时不查询主档也不创建账号`() {
        whenever(accessRecordRepository.findDistinctCarNumbersSince(any())).thenReturn(emptyList())

        val result = task.generate()

        verify(carMasterInfoService, never()).getAllList()
        verify(userService, never()).create(any())
        assertEquals(0, result.createdCount)
        assertEquals(0, result.skippedCount)
    }

    @Test
    fun `手动触发生成后记录成功执行历史`() {
        whenever(accessRecordRepository.findDistinctCarNumbersSince(any())).thenReturn(listOf("沪A12345"))
        whenever(carMasterInfoService.getAllList()).thenReturn(
            listOf(carMaster(1L, "张三", "13800138000", "沪A12345")),
        )
        whenever(userService.findExistingUsernames(setOf("13800138000"))).thenReturn(emptySet())
        val command = argumentCaptor<SyncTaskRunCommand>()

        task.generate()

        verify(historyService).record(command.capture())
        assertEquals("account.generate", command.firstValue.taskKey)
        assertEquals("车辆业主账号生成", command.firstValue.taskName)
        assertEquals(SyncTaskRun.Trigger.MANUAL, command.firstValue.trigger)
        assertEquals(SyncTaskRun.Status.SUCCESS, command.firstValue.status)
        assertEquals(1, command.firstValue.processedCount)
        assertNull(command.firstValue.error)
    }

    @Test
    fun `整体失败时记录失败执行历史并抛出异常`() {
        whenever(accessRecordRepository.findDistinctCarNumbersSince(any())).thenThrow(IllegalStateException("查询失败"))

        val exception = runCatching { task.generate() }.exceptionOrNull()

        assertTrue(exception is IllegalStateException)
        val command = argumentCaptor<SyncTaskRunCommand>()
        verify(historyService).record(command.capture())
        assertEquals(SyncTaskRun.Trigger.MANUAL, command.firstValue.trigger)
        assertEquals(SyncTaskRun.Status.FAILED, command.firstValue.status)
        assertEquals("查询失败", command.firstValue.error)
    }

    @Test
    fun `定时触发记录定时执行历史`() {
        whenever(accessRecordRepository.findDistinctCarNumbersSince(any())).thenReturn(emptyList())

        task.synAccountGenerate()

        val command = argumentCaptor<SyncTaskRunCommand>()
        verify(historyService).record(command.capture())
        assertEquals(SyncTaskRun.Trigger.SCHEDULED, command.firstValue.trigger)
    }
}
