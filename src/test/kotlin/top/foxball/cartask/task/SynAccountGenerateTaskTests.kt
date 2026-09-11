package top.foxball.cartask.task

import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.scheduling.annotation.Scheduled
import top.foxball.cartask.entity.ParkingOwner
import top.foxball.cartask.entity.ParkingPlate
import top.foxball.cartask.entity.SyncTaskRun
import top.foxball.cartask.repository.AccessRecordRepository
import top.foxball.cartask.repository.ParkingOwnerRepository
import top.foxball.cartask.repository.ParkingPlateRepository
import top.foxball.cartask.service.SyncTaskHistoryService
import top.foxball.cartask.service.SyncTaskRunCommand
import top.foxball.cartask.service.UserService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SynAccountGenerateTaskTests {
    private val userService = mock<UserService>()
    private val parkingPlateRepository = mock<ParkingPlateRepository>()
    private val parkingOwnerRepository = mock<ParkingOwnerRepository>()
    private val accessRecordRepository = mock<AccessRecordRepository>()
    private val historyService = mock<SyncTaskHistoryService>()
    private val task = SynAccountGenerateTask(
        userService,
        parkingPlateRepository,
        parkingOwnerRepository,
        accessRecordRepository,
        historyService,
    )

    private val savedAt = LocalDateTime.of(2026, 1, 1, 0, 0)

    private fun plate(value: String, ownerId: Long, status: Int = 1): ParkingPlate = ParkingPlate().apply {
        plate = value
        owner = "车主"
        this.ownerId = ownerId
        this.status = status
        regDate = LocalDate.of(2026, 1, 1)
        createdAt = savedAt
        updatedAt = savedAt
    }

    private fun owner(id: Long, name: String, phone: String, status: Int = 1): ParkingOwner = ParkingOwner().apply {
        this.id = id
        cardId = "CARD-$id"
        this.name = name
        dept = "运营部"
        this.phone = phone
        spotCount = 1
        plateCount = 1
        balance = BigDecimal.ZERO
        this.status = status
        createdAt = savedAt
        updatedAt = savedAt
    }

    /** 模拟车牌档案与车主档案的关联查询。 */
    private fun prepareArchives(vararg plates: ParkingPlate, owners: List<ParkingOwner> = emptyList()) {
        whenever(parkingPlateRepository.findAll()).thenReturn(plates.toList())
        whenever(parkingOwnerRepository.findAllById(any())).thenAnswer { invocation ->
            val ids = invocation.getArgument<Collection<Long>>(0)
            owners.filter { it.id in ids }
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
        whenever(accessRecordRepository.findDistinctCarNumbersSince(any())).thenReturn(listOf("粤A12345"))
        prepareArchives(
            plate("粤A·12345", ownerId = 1),
            owners = listOf(owner(1, "张三", " 13800138000 ")),
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
    fun `跳过无档案停用档案和无手机号的活跃车牌`() {
        whenever(accessRecordRepository.findDistinctCarNumbersSince(any()))
            .thenReturn(listOf("沪A00001", "沪A00002", "沪A00003", "沪A00004"))
        prepareArchives(
            plate("沪A00002", ownerId = 2, status = 0),
            plate("沪A00003", ownerId = 3),
            plate("沪A00004", ownerId = 4),
            owners = listOf(
                owner(3, "王五", "13800000003", status = 0),
                owner(4, "赵六", " "),
            ),
        )
        whenever(userService.findExistingUsernames(emptySet())).thenReturn(emptySet())

        val result = task.generate()

        verify(userService, never()).create(any())
        assertEquals(0, result.createdCount)
        assertEquals(4, result.skippedCount)
    }

    @Test
    fun `多个车牌指向同一业主时只处理一次且失败不中断后续`() {
        whenever(accessRecordRepository.findDistinctCarNumbersSince(any()))
            .thenReturn(listOf("沪A00001", "沪A00002", "沪A00003"))
        prepareArchives(
            plate("沪A00001", ownerId = 1),
            plate("沪A00002", ownerId = 1),
            plate("沪A00003", ownerId = 2),
            owners = listOf(
                owner(1, "张三", "13800138000"),
                owner(2, "李四", "13800138001"),
            ),
        )
        whenever(
            userService.findExistingUsernames(setOf("13800138000", "13800138001")),
        ).thenReturn(emptySet())
        whenever(userService.create(any())).thenThrow(IllegalStateException("创建失败"))
        val command = argumentCaptor<UserService.CreateCommand>()

        val result = task.generate()

        verify(userService).findExistingUsernames(setOf("13800138000", "13800138001"))
        verify(userService, times(2)).create(command.capture())
        assertEquals("13800138000", command.allValues[0].username)
        assertEquals("13800138001", command.allValues[1].username)
        assertEquals(0, result.createdCount)
        assertEquals(2, result.failedCount)
        assertEquals(1, result.skippedCount)
    }

    @Test
    fun `近三十天没有进出记录时不查询档案也不创建账号`() {
        whenever(accessRecordRepository.findDistinctCarNumbersSince(any())).thenReturn(emptyList())

        val result = task.generate()

        verify(parkingPlateRepository, never()).findAll()
        verify(userService, never()).create(any())
        assertEquals(0, result.createdCount)
        assertEquals(0, result.skippedCount)
    }

    @Test
    fun `手动触发生成后记录成功执行历史`() {
        whenever(accessRecordRepository.findDistinctCarNumbersSince(any())).thenReturn(listOf("粤A12345"))
        prepareArchives(
            plate("粤A12345", ownerId = 1),
            owners = listOf(owner(1, "张三", "13800138000")),
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
