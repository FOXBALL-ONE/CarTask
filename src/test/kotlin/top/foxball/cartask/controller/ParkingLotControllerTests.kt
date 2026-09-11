package top.foxball.cartask.controller

import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.security.access.prepost.PreAuthorize
import top.foxball.cartask.entity.ParkingLot
import top.foxball.cartask.keytop.KeytopProperties
import top.foxball.cartask.repository.ParkingLotRepository
import top.foxball.cartask.shared.ResponseBuilder
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ParkingLotControllerTests {
    private val lotRepository = mock<ParkingLotRepository>()
    private val controller = ParkingLotController(lotRepository, KeytopProperties(parkId = "591007282"), ResponseBuilder())

    @Test
    fun `返回当前车场的同步详情`() {
        val lot = ParkingLot().apply {
            parkCode = "591007282"
            name = "示例停车场"
            totalPlaceCount = 83
            areaCount = 3
            parkArea = "4"
            createdAt = LocalDateTime.now()
            updatedAt = LocalDateTime.now()
        }
        whenever(lotRepository.findByParkCode("591007282")).thenReturn(lot)

        val response = controller.get()

        assertEquals(200, response.statusCode.value())
        assertEquals(lot, response.body?.data)
    }

    @Test
    fun `尚未同步时返回空数据`() {
        whenever(lotRepository.findByParkCode("591007282")).thenReturn(null)

        val response = controller.get()

        assertEquals(200, response.statusCode.value())
        val data = response.body?.data
        assertTrue(data is Map<*, *>)
        assertTrue(data.isEmpty())
    }

    @Test
    fun `查询车场详情要求字典读权限`() {
        val annotation = ParkingLotController::class.java.getAnnotation(PreAuthorize::class.java)

        assertEquals("hasAuthority('dictionary:read')", annotation.value)
    }
}
