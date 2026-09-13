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
import tools.jackson.databind.ObjectMapper
import top.foxball.cartask.entity.ParkingOwner
import top.foxball.cartask.entity.ParkingPlate
import top.foxball.cartask.entity.SyncTaskRun
import top.foxball.cartask.keytop.KeytopResponse
import top.foxball.cartask.keytop.KeytopService
import top.foxball.cartask.repository.AccessRecordRepository
import top.foxball.cartask.repository.ParkingOwnerRepository
import top.foxball.cartask.repository.ParkingPlateRepository
import top.foxball.cartask.service.SyncTaskHistoryService
import top.foxball.cartask.service.SyncTaskRunCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SynOwnerArchiveTaskTests {
    private val parkingPlateRepository = mock<ParkingPlateRepository>()
    private val parkingOwnerRepository = mock<ParkingOwnerRepository>()
    private val accessRecordRepository = mock<AccessRecordRepository>()
    private val historyService = mock<SyncTaskHistoryService>()
    private val keytopService = mock<KeytopService>()
    private val objectMapper = ObjectMapper()
    private val task = SynOwnerArchiveTask(
        parkingPlateRepository,
        parkingOwnerRepository,
        accessRecordRepository,
        historyService,
        keytopService = keytopService,
        objectMapper = objectMapper,
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

    /** 模拟车牌档案与车主档案的全量查询。 */
    private fun prepareArchives(vararg plates: ParkingPlate, owners: List<ParkingOwner> = emptyList()) {
        whenever(parkingPlateRepository.findAll()).thenReturn(plates.toList())
        whenever(parkingOwnerRepository.findAll()).thenReturn(owners)
    }

    /** 模拟补建档案的落库：真实 JPA 会把自增主键回填到实体上。 */
    private fun stubArchivePersistence() {
        whenever(parkingOwnerRepository.save(any())).thenAnswer { invocation ->
            invocation.getArgument<ParkingOwner>(0).apply { id = 900L }
        }
        whenever(parkingPlateRepository.save(any())).thenAnswer { invocation ->
            invocation.getArgument<ParkingPlate>(0).apply { id = 901L }
        }
    }

    /** 模拟科拓按车牌返回的卡片信息。 */
    private fun stubCardInfo(plate: String, cardId: String, name: String, phone: String) {
        whenever(keytopService.getCardInfoByUser(plate)).thenReturn(
            KeytopResponse(
                0,
                "success",
                objectMapper.readTree(
                    """{"cardInfo":{"cardName":"$cardId","useName":"$name","tel":"$phone"}}""",
                ),
            ),
        )
    }

    @Test
    fun `车牌没有档案时回查科拓补建车主档案与车牌关联`() {
        whenever(accessRecordRepository.findDistinctCarNumbersSince(any())).thenReturn(listOf("沪A00001"))
        prepareArchives()
        stubArchivePersistence()
        stubCardInfo("沪A00001", cardId = "CARD-9", name = "张三", phone = "13800138000")
        val ownerCaptor = argumentCaptor<ParkingOwner>()
        val plateCaptor = argumentCaptor<ParkingPlate>()
        val startTimeCaptor = argumentCaptor<LocalDateTime>()

        val result = task.generate()

        verify(accessRecordRepository).findDistinctCarNumbersSince(startTimeCaptor.capture())
        val days = Duration.between(startTimeCaptor.firstValue, LocalDateTime.now()).toDays()
        assertTrue(days in 29..31, "查询范围应回溯约 30 天，实际 ${days} 天")
        verify(parkingOwnerRepository).save(ownerCaptor.capture())
        assertEquals("CARD-9", ownerCaptor.firstValue.cardId)
        assertEquals("张三", ownerCaptor.firstValue.name)
        assertEquals("13800138000", ownerCaptor.firstValue.phone)
        assertEquals("同步车主", ownerCaptor.firstValue.dept)
        assertEquals(1, ownerCaptor.firstValue.status)
        verify(parkingPlateRepository).save(plateCaptor.capture())
        assertEquals("沪A00001", plateCaptor.firstValue.plate)
        assertEquals(900L, plateCaptor.firstValue.ownerId)
        assertEquals("张三", plateCaptor.firstValue.owner)
        assertEquals(1, plateCaptor.firstValue.status)
        assertEquals(LocalDate.now(), plateCaptor.firstValue.regDate)
        assertEquals(1, result.createdOwnerCount)
        assertEquals(1, result.linkedPlateCount)
        assertEquals(0, result.skippedCount)
    }

    @Test
    fun `同一手机号的多个车牌只建一条车主档案`() {
        whenever(accessRecordRepository.findDistinctCarNumbersSince(any()))
            .thenReturn(listOf("沪A00001", "沪A00002"))
        prepareArchives()
        stubArchivePersistence()
        stubCardInfo("沪A00001", cardId = "CARD-9", name = "张三", phone = "13800138000")
        stubCardInfo("沪A00002", cardId = "CARD-9", name = "张三", phone = "13800138000")

        val result = task.generate()

        verify(parkingOwnerRepository, times(1)).save(any())
        verify(parkingPlateRepository, times(2)).save(any())
        assertEquals(1, result.createdOwnerCount)
        assertEquals(2, result.linkedPlateCount)
        assertEquals(0, result.skippedCount)
    }

    @Test
    fun `科拓没有返回卡片信息时不补建车主档案`() {
        whenever(accessRecordRepository.findDistinctCarNumbersSince(any())).thenReturn(listOf("沪A00001"))
        prepareArchives()
        whenever(keytopService.getCardInfoByUser("沪A00001"))
            .thenReturn(KeytopResponse(0, "success", objectMapper.readTree("""{"cardInfo":{}}""")))

        val result = task.generate()

        verify(parkingOwnerRepository, never()).save(any())
        verify(parkingPlateRepository, never()).save(any())
        assertEquals(0, result.createdOwnerCount)
        assertEquals(0, result.linkedPlateCount)
        assertEquals(1, result.skippedCount)
    }

    @Test
    fun `科拓查询失败时只跳过当前车牌`() {
        whenever(accessRecordRepository.findDistinctCarNumbersSince(any()))
            .thenReturn(listOf("沪A00001", "沪A00002"))
        prepareArchives()
        stubArchivePersistence()
        whenever(keytopService.getCardInfoByUser("沪A00001")).thenThrow(IllegalStateException("科拓不可用"))
        stubCardInfo("沪A00002", cardId = "CARD-9", name = "张三", phone = "13800138000")

        val result = task.generate()

        verify(parkingOwnerRepository, times(1)).save(any())
        assertEquals(1, result.createdOwnerCount)
        assertEquals(1, result.linkedPlateCount)
        assertEquals(1, result.skippedCount)
    }

    @Test
    fun `已是有效档案与在营车主时直接跳过且不访问科拓`() {
        whenever(accessRecordRepository.findDistinctCarNumbersSince(any())).thenReturn(listOf("粤A12345"))
        prepareArchives(
            plate("粤A·12345", ownerId = 1),
            owners = listOf(owner(1, "张三", "13800138000")),
        )

        val result = task.generate()

        verify(keytopService, never()).getCardInfoByUser(any())
        verify(parkingOwnerRepository, never()).save(any())
        verify(parkingPlateRepository, never()).save(any())
        assertEquals(0, result.createdOwnerCount)
        assertEquals(0, result.linkedPlateCount)
        assertEquals(1, result.skippedCount)
    }

    @Test
    fun `卡号已存在的车主直接复用不再新建档案`() {
        whenever(accessRecordRepository.findDistinctCarNumbersSince(any())).thenReturn(listOf("沪A00001"))
        prepareArchives(owners = listOf(owner(1, "张三", "13800138000")))
        stubArchivePersistence()
        stubCardInfo("沪A00001", cardId = "CARD-1", name = "张三", phone = "13800138000")
        val plateCaptor = argumentCaptor<ParkingPlate>()

        val result = task.generate()

        verify(parkingOwnerRepository, never()).save(any())
        verify(parkingPlateRepository).save(plateCaptor.capture())
        assertEquals(1L, plateCaptor.firstValue.ownerId)
        assertEquals(0, result.createdOwnerCount)
        assertEquals(1, result.linkedPlateCount)
    }

    @Test
    fun `停用的车牌档案不补建`() {
        whenever(accessRecordRepository.findDistinctCarNumbersSince(any())).thenReturn(listOf("沪A00001"))
        prepareArchives(plate("沪A00001", ownerId = 1, status = 0))

        val result = task.generate()

        verify(keytopService, never()).getCardInfoByUser(any())
        verify(parkingPlateRepository, never()).save(any())
        assertEquals(0, result.createdOwnerCount)
        assertEquals(0, result.linkedPlateCount)
        assertEquals(1, result.skippedCount)
    }

    @Test
    fun `停用车主按卡号命中已有档案时跳过不新建`() {
        whenever(accessRecordRepository.findDistinctCarNumbersSince(any()))
            .thenReturn(listOf("沪A00001", "沪A00002"))
        prepareArchives(
            plate("沪A00001", ownerId = 3),
            owners = listOf(owner(3, "王五", "13800000003", status = 0)),
        )
        stubArchivePersistence()
        stubCardInfo("沪A00001", cardId = "CARD-3", name = "王五", phone = "13800000003")
        stubCardInfo("沪A00002", cardId = "CARD-9", name = "张三", phone = "13800138000")

        val result = task.generate()

        verify(parkingOwnerRepository, times(1)).save(any())
        assertEquals(1, result.createdOwnerCount)
        assertEquals(1, result.linkedPlateCount)
        assertEquals(1, result.skippedCount)
    }

    @Test
    fun `近三十天没有进出记录时不查询档案也不补建`() {
        whenever(accessRecordRepository.findDistinctCarNumbersSince(any())).thenReturn(emptyList())

        val result = task.generate()

        verify(parkingPlateRepository, never()).findAll()
        verify(parkingOwnerRepository, never()).save(any())
        assertEquals(0, result.createdOwnerCount)
        assertEquals(0, result.linkedPlateCount)
        assertEquals(0, result.skippedCount)
    }

    @Test
    fun `手动触发后记录成功执行历史`() {
        whenever(accessRecordRepository.findDistinctCarNumbersSince(any())).thenReturn(listOf("沪A00001"))
        prepareArchives()
        stubArchivePersistence()
        stubCardInfo("沪A00001", cardId = "CARD-9", name = "张三", phone = "13800138000")
        val command = argumentCaptor<SyncTaskRunCommand>()

        task.generate()

        verify(historyService).record(command.capture())
        assertEquals("owner.archive.generate", command.firstValue.taskKey)
        assertEquals("车主档案补建", command.firstValue.taskName)
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

        task.synOwnerArchive()

        val command = argumentCaptor<SyncTaskRunCommand>()
        verify(historyService).record(command.capture())
        assertEquals(SyncTaskRun.Trigger.SCHEDULED, command.firstValue.trigger)
    }
}
