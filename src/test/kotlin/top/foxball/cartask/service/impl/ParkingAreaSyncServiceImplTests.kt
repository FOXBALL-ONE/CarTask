package top.foxball.cartask.service.impl

import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import tools.jackson.databind.ObjectMapper
import top.foxball.cartask.entity.ParkingLot
import top.foxball.cartask.entity.type.ZoneType
import top.foxball.cartask.handler.ParkingAreaSyncInProgressException
import top.foxball.cartask.keytop.KeytopProperties
import top.foxball.cartask.keytop.KeytopResponse
import top.foxball.cartask.keytop.KeytopService
import top.foxball.cartask.repository.ParkingLotRepository
import top.foxball.cartask.repository.ZoneTypeRepository
import top.foxball.cartask.service.ParkingAreaSyncResult
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ParkingAreaSyncServiceImplTests {
    private val keytopService = mock<KeytopService>()
    private val repository = mock<ZoneTypeRepository>()
    private val lotRepository = mock<ParkingLotRepository>()
    private val objectMapper = ObjectMapper()
    private val properties = KeytopProperties(parkId = "591007282", parkName = "示例停车场")
    private val service = ParkingAreaSyncServiceImpl(keytopService, repository, lotRepository, properties, objectMapper)

    @Test
    fun `幂等新增更新并统计科拓停车区域`() {
        whenever(keytopService.getParkingPlaceArea()).thenReturn(
            KeytopResponse(
                code = 0,
                message = "success",
                data = objectMapper.readTree(
                    """{"totalPlaceCount":83,"parkArea":"4","areaInfo":[{"areaCode":1,"areaName":"地面区","placeCount":41},{"areaCode":2,"areaName":"地下区","placeCount":32},{"areaCode":3,"areaName":"访客区","placeCount":10}]}""",
                ),
            ),
        )
        val updated = ZoneType().apply {
            zoneCode = "2"
            zoneName = "旧名称"
            orderNumber = 99
            placeCount = 99
        }
        val unchanged = ZoneType().apply {
            zoneCode = "3"
            zoneName = "访客区"
            orderNumber = 3
            placeCount = 10
        }
        whenever(repository.findAllByZoneCodeInOrZoneCodeIsNull(setOf("1", "2", "3")))
            .thenReturn(listOf(updated, unchanged))
        whenever(lotRepository.findByParkCode("591007282")).thenReturn(null)
        whenever(lotRepository.save(any())).thenAnswer { it.getArgument<ParkingLot>(0) }
        val captor = argumentCaptor<List<ZoneType>>()
        val lotCaptor = argumentCaptor<ParkingLot>()

        val result = service.synchronize()

        verify(repository).saveAll(captor.capture())
        assertEquals(2, captor.firstValue.size)
        val created = captor.firstValue.first { it.zoneCode == "1" }
        assertEquals("地面区", created.zoneName)
        assertEquals(1, created.orderNumber)
        assertEquals("地下区", updated.zoneName)
        assertEquals(2, updated.orderNumber)
        assertEquals(32, updated.placeCount)
        assertEquals(3, result.receivedCount)
        assertEquals(1, result.createdCount)
        assertEquals(1, result.updatedCount)
        assertEquals(1, result.unchangedCount)
        verify(lotRepository).save(lotCaptor.capture())
        val lot = lotCaptor.firstValue
        assertEquals("591007282", lot.parkCode)
        assertEquals("示例停车场", lot.name)
        assertEquals(83, lot.totalPlaceCount)
        assertEquals("4", lot.parkArea)
        assertEquals(3, lot.areaCount)
        assertEquals("示例停车场", result.lotName)
        assertEquals(83, result.totalPlaceCount)
    }

    @Test
    fun `兼容字符串形式的 data 和 areaInfo 以及蛇形字段`() {
        whenever(keytopService.getParkingPlaceArea()).thenReturn(
            KeytopResponse(
                0,
                "success",
                objectMapper.valueToTree(
                    """{"areaInfo":"[{\"area_code\":\"A-1\",\"area_name\":\"地库 A 区\",\"place_count\":12}]"}""",
                ),
            ),
        )
        whenever(repository.findAllByZoneCodeInOrZoneCodeIsNull(setOf("A-1"))).thenReturn(emptyList())
        whenever(lotRepository.save(any())).thenAnswer { it.getArgument<ParkingLot>(0) }
        val captor = argumentCaptor<List<ZoneType>>()

        val result = service.synchronize()

        verify(repository).saveAll(captor.capture())
        assertEquals("A-1", captor.firstValue.single().zoneCode)
        assertEquals("地库 A 区", captor.firstValue.single().zoneName)
        assertEquals(12, captor.firstValue.single().placeCount)
        assertEquals(1, result.createdCount)
    }

    @Test
    fun `复用同名的旧区域并补充科拓编码`() {
        whenever(keytopService.getParkingPlaceArea()).thenReturn(
            KeytopResponse(
                0,
                "success",
                objectMapper.readTree("""{"areaInfo":[{"areaCode":8,"areaName":"地面区","placeCount":20}]}"""),
            ),
        )
        val legacy = ZoneType().apply {
            zoneName = "地面区"
            orderNumber = 1
        }
        whenever(repository.findAllByZoneCodeInOrZoneCodeIsNull(setOf("8"))).thenReturn(listOf(legacy))
        whenever(lotRepository.save(any())).thenAnswer { it.getArgument<ParkingLot>(0) }

        val result = service.synchronize()

        verify(repository).saveAll(listOf(legacy))
        assertEquals("8", legacy.zoneCode)
        assertEquals(8, legacy.orderNumber)
        assertEquals(20, legacy.placeCount)
        assertEquals(1, result.updatedCount)
    }

    @Test
    fun `更新已有停车场详情且响应缺失的字段不覆盖`() {
        whenever(keytopService.getParkingPlaceArea()).thenReturn(
            KeytopResponse(
                0,
                "success",
                objectMapper.readTree("""{"areaInfo":[{"areaCode":1,"areaName":"地面区","placeCount":41}]}"""),
            ),
        )
        val existingLot = ParkingLot().apply {
            parkCode = "591007282"
            name = "旧名称"
            totalPlaceCount = 41
            parkArea = "4"
        }
        whenever(lotRepository.findByParkCode("591007282")).thenReturn(existingLot)
        whenever(lotRepository.save(any())).thenAnswer { it.getArgument<ParkingLot>(0) }

        val result = service.synchronize()

        assertEquals("示例停车场", existingLot.name)
        assertEquals(41, existingLot.totalPlaceCount)
        assertEquals("4", existingLot.parkArea)
        assertEquals(1, existingLot.areaCount)
        assertEquals("示例停车场", result.lotName)
        assertNull(result.totalPlaceCount)
    }

    @Test
    fun `未配置车场编号时跳过停车场详情保存`() {
        whenever(keytopService.getParkingPlaceArea()).thenReturn(
            KeytopResponse(
                0,
                "success",
                objectMapper.readTree("""{"totalPlaceCount":83,"areaInfo":[{"areaCode":1,"areaName":"地面区","placeCount":41}]}"""),
            ),
        )
        val serviceWithoutParkId = ParkingAreaSyncServiceImpl(
            keytopService,
            repository,
            lotRepository,
            KeytopProperties(),
            objectMapper,
        )

        val result = serviceWithoutParkId.synchronize()

        verify(lotRepository, never()).findByParkCode(any())
        verify(lotRepository, never()).save(any<ParkingLot>())
        assertNull(result.lotName)
        assertEquals(83, result.totalPlaceCount)
    }

    @Test
    fun `科拓返回失败时抛出异常且不写入字典`() {
        whenever(keytopService.getParkingPlaceArea()).thenReturn(KeytopResponse(1, "failed", null))

        val exception = assertFailsWith<IllegalArgumentException> { service.synchronize() }

        assertEquals("Keytop 停车区域接口返回失败：1 failed", exception.message)
        verify(repository, never()).saveAll(any<List<ZoneType>>())
        verify(lotRepository, never()).save(any<ParkingLot>())
    }

    @Test
    fun `科拓返回空区域时不查询和写入字典`() {
        whenever(keytopService.getParkingPlaceArea()).thenReturn(
            KeytopResponse(0, "success", objectMapper.readTree("""{"areaInfo":[]}""")),
        )

        val result = service.synchronize()

        assertEquals(ParkingAreaSyncResult(0, 0, 0, 0), result)
        verify(repository, never()).findAllByZoneCodeInOrZoneCodeIsNull(any())
        verify(repository, never()).saveAll(any<List<ZoneType>>())
        verify(lotRepository, never()).save(any<ParkingLot>())
    }

    @Test
    fun `已有同步执行时拒绝重复触发`() {
        val requestStarted = CountDownLatch(1)
        val releaseRequest = CountDownLatch(1)
        whenever(keytopService.getParkingPlaceArea()).thenAnswer {
            requestStarted.countDown()
            releaseRequest.await(5, TimeUnit.SECONDS)
            KeytopResponse(0, "success", objectMapper.readTree("""{"areaInfo":[]}"""))
        }
        val executor = Executors.newSingleThreadExecutor()
        val firstRequest = executor.submit<ParkingAreaSyncResult> { service.synchronize() }

        try {
            requestStarted.await(5, TimeUnit.SECONDS)

            assertFailsWith<ParkingAreaSyncInProgressException> { service.synchronize() }
        } finally {
            releaseRequest.countDown()
            firstRequest.get(5, TimeUnit.SECONDS)
            executor.shutdownNow()
        }
    }
}
