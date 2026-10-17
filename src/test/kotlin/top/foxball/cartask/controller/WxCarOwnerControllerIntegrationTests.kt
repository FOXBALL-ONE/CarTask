package top.foxball.cartask.controller

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import top.foxball.cartask.authentication.CurrentUserPrincipal
import top.foxball.cartask.entity.ParkingOwner
import top.foxball.cartask.entity.ParkingPlate
import top.foxball.cartask.entity.ParkingSpot
import top.foxball.cartask.repository.ParkingOwnerRepository
import top.foxball.cartask.repository.ParkingPlateRepository
import top.foxball.cartask.repository.ParkingSpotRepository
import top.foxball.cartask.scope.WithCurrentUser
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@SpringBootTest(
    properties = [
        "app.mock-data.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:wx_car_owner_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    ],
)
@ActiveProfiles("test")
class WxCarOwnerControllerIntegrationTests(
    @Autowired private val controller: WxCarOwnerController,
    @Autowired private val ownerRepository: ParkingOwnerRepository,
    @Autowired private val plateRepository: ParkingPlateRepository,
    @Autowired private val spotRepository: ParkingSpotRepository,
) {
    @BeforeEach
    fun clearOwners() {
        plateRepository.deleteAll()
        spotRepository.deleteAll()
        ownerRepository.deleteAll()
    }

    @Test
    @WithCurrentUser(userId = 9301, username = "owner-user", role = "USER")
    fun `普通用户列表和详情仅包含本人关联的车主`() {
        val currentOwner = ownerRepository.save(
            ParkingOwner().apply {
                cardId = "WX-OWNER-9301"
                name = "微信车主甲"
                dept = "机关事务服务中心"
                phone = "13800009301"
                linkedUserId = 9301
            },
        )
        val anotherOwner = ownerRepository.save(
            ParkingOwner().apply {
                cardId = "WX-OWNER-9302"
                name = "微信车主乙"
                dept = "其他部门"
                phone = "13800009302"
                linkedUserId = 9302
            },
        )
        plateRepository.save(
            ParkingPlate().apply {
                plate = "闽A93001"
                owner = currentOwner.name
                ownerId = requireNotNull(currentOwner.id)
                regDate = LocalDate.of(2025, 1, 2)
                carBrand = "测试汽车"
            },
        )
        spotRepository.save(
            ParkingSpot().apply {
                code = "A-9301"
                area = "机关停车场"
                type = "固定车位"
                owner = currentOwner.name
                ownerCode = currentOwner.cardId
                status = 1
            },
        )
        val principal = CurrentUserPrincipal(9301, "owner-user", "USER", "owner-token")

        val page = controller.listOwners(principal, null, 1, 12).body!!.data!!
        val items = page.javaClass.getMethod("getItems").invoke(page) as List<*>
        val ownerIds = items.map { it!!.javaClass.getMethod("getId").invoke(it) }

        assertEquals(listOf(currentOwner.id), ownerIds)
        val detail = controller.getOwner(principal, requireNotNull(currentOwner.id)).body!!.data!!
        val plates = detail.javaClass.getMethod("getPlates").invoke(detail) as List<*>
        val spots = detail.javaClass.getMethod("getSpots").invoke(detail) as List<*>
        assertEquals(1, plates.size)
        assertEquals("闽A93001", plates.single()!!.javaClass.getMethod("getPlate").invoke(plates.single()))
        assertEquals(1, spots.size)
        assertEquals("A-9301", spots.single()!!.javaClass.getMethod("getCode").invoke(spots.single()))
        assertFailsWith<IllegalArgumentException> {
            controller.getOwner(principal, requireNotNull(anotherOwner.id))
        }
    }
}
