package top.foxball.cartask.service

import java.time.LocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import top.foxball.cartask.config.DashboardProperties
import top.foxball.cartask.entity.ParkingSpot
import top.foxball.cartask.entity.type.ZoneType
import top.foxball.cartask.repository.ParkingSpotRepository
import top.foxball.cartask.repository.ZoneTypeRepository
import top.foxball.cartask.scope.DataScope
import top.foxball.cartask.scope.ScopeQuerySupport

class DashboardSpotStatsServiceTests {
    private val zoneTypeRepository = mock<ZoneTypeRepository>()
    private val parkingSpotRepository = mock<ParkingSpotRepository>()
    private val scopeQuerySupport = mock<ScopeQuerySupport>()
    private val properties = DashboardProperties(listOf("1", "2"))
    private val service = DashboardSpotStatsService(
        zoneTypeRepository,
        parkingSpotRepository,
        scopeQuerySupport,
        properties,
    )

    private fun zone(code: String, name: String, placeCount: Int, status: ZoneType.Status = ZoneType.Status.Activity, order: Int = 0) =
        ZoneType().apply {
            zoneCode = code
            zoneName = name
            this.placeCount = placeCount
            this.status = status
            orderNumber = order
            createdAt = LocalDateTime.of(2026, 1, 1, 0, 0)
            updatedAt = createdAt
        }

    private fun spot(code: String, area: String, status: Int, ownerCode: String? = null) = ParkingSpot().apply {
        this.code = code
        this.area = area
        type = "标准车位"
        this.status = status
        this.ownerCode = ownerCode
        createdAt = LocalDateTime.of(2026, 1, 1, 0, 0)
        updatedAt = createdAt
    }

    @Test
    fun `车位总数取车场的区域容量而不是本地登记行数`() {
        whenever(zoneTypeRepository.findAllByZoneCodeIn(listOf("1", "2")))
            .thenReturn(listOf(zone("1", "一号车场", 300), zone("2", "二号车场", 200)))
        // 本地只登记了 2 个车位，容量仍是 500——这正是原先「车位总数」不准的原因。
        whenever(parkingSpotRepository.findAll())
            .thenReturn(listOf(spot("A-1", "一号车场", 1), spot("B-1", "二号车场", 0)))

        val stats = service.currentStats(DataScope.All)

        assertEquals(500, stats.total)
        assertEquals(1, stats.used)
        assertEquals(listOf("一号车场" to 300, "二号车场" to 200), stats.zones.map { it.zoneName to it.total })
    }

    @Test
    fun `只统计配置区域内的车位其他区域不计入`() {
        whenever(zoneTypeRepository.findAllByZoneCodeIn(listOf("1", "2")))
            .thenReturn(listOf(zone("1", "一号车场", 100)))
        whenever(parkingSpotRepository.findAll()).thenReturn(
            listOf(
                spot("A-1", "一号车场", 1),
                spot("C-1", "三号车场", 1),
            ),
        )

        val stats = service.currentStats(DataScope.All)

        assertEquals(100, stats.total)
        assertEquals(1, stats.used)
    }

    @Test
    fun `停用区域不计入容量`() {
        whenever(zoneTypeRepository.findAllByZoneCodeIn(listOf("1", "2")))
            .thenReturn(
                listOf(
                    zone("1", "一号车场", 300),
                    zone("2", "二号车场", 200, status = ZoneType.Status.BANNED),
                ),
            )
        whenever(parkingSpotRepository.findAll()).thenReturn(emptyList())

        val stats = service.currentStats(DataScope.All)

        assertEquals(300, stats.total)
        assertEquals(listOf("一号车场"), stats.zones.map { it.zoneName })
    }

    @Test
    fun `配置的区域还没同步到时不报错只是容量为零`() {
        whenever(zoneTypeRepository.findAllByZoneCodeIn(listOf("1", "2"))).thenReturn(emptyList())

        val stats = service.currentStats(DataScope.All)

        assertEquals(0, stats.total)
        assertEquals(0, stats.used)
        assertTrue(stats.zones.isEmpty())
        verify(parkingSpotRepository, never()).findAll()
    }

    @Test
    fun `区域按显示顺序返回`() {
        whenever(zoneTypeRepository.findAllByZoneCodeIn(listOf("1", "2")))
            .thenReturn(listOf(zone("2", "二号车场", 200, order = 2), zone("1", "一号车场", 300, order = 1)))
        whenever(parkingSpotRepository.findAll()).thenReturn(emptyList())

        val stats = service.currentStats(DataScope.All)

        assertEquals(listOf("一号车场", "二号车场"), stats.zones.map { it.zoneName })
    }

    @Test
    fun `受限范围下已分配只算范围内的车位而容量仍是车场的`() {
        whenever(zoneTypeRepository.findAllByZoneCodeIn(listOf("1", "2")))
            .thenReturn(listOf(zone("1", "一号车场", 300)))
        whenever(parkingSpotRepository.findAll()).thenReturn(
            listOf(
                spot("A-1", "一号车场", 1, ownerCode = "CARD-1"),
                spot("A-2", "一号车场", 1, ownerCode = "CARD-2"),
            ),
        )
        whenever(scopeQuerySupport.ownerCardIdsInScope(any())).thenReturn(setOf("CARD-1"))
        whenever(scopeQuerySupport.spotVisible(setOf("CARD-1"), "CARD-1")).thenReturn(true)
        whenever(scopeQuerySupport.spotVisible(setOf("CARD-1"), "CARD-2")).thenReturn(false)

        val stats = service.currentStats(
            DataScope.departments(setOf(1L), setOf("PARKING"), setOf("停车管理组")),
        )

        assertEquals(300, stats.total)
        assertEquals(1, stats.used)
    }
}
