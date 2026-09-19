package top.foxball.cartask.service

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import tools.jackson.databind.ObjectMapper
import top.foxball.cartask.audit.AuditService
import top.foxball.cartask.entity.ParkingOwner
import top.foxball.cartask.entity.ParkingPlate
import top.foxball.cartask.entity.ParkingPlateKeytopSyncTask
import top.foxball.cartask.keytop.KeytopCardInfo
import top.foxball.cartask.keytop.KeytopCarLot
import top.foxball.cartask.keytop.KeytopPayCarCardFeeRequest
import top.foxball.cartask.keytop.KeytopPlateNo
import top.foxball.cartask.keytop.KeytopResponse
import top.foxball.cartask.keytop.KeytopService
import top.foxball.cartask.repository.ParkingOwnerRepository
import top.foxball.cartask.repository.ParkingPlateKeytopSyncTaskRepository
import top.foxball.cartask.repository.ParkingPlateRepository
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

@SpringBootTest(properties = [
    "app.mock-data.enabled=false",
    "spring.task.scheduling.enabled=false",
    "spring.datasource.url=jdbc:h2:mem:plate_keytop_sync_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
])
@ActiveProfiles("test")
class PlateKeytopSyncServiceIntegrationTests(
    @Autowired private val service: PlateKeytopSyncService,
    @Autowired private val owners: ParkingOwnerRepository,
    @Autowired private val plates: ParkingPlateRepository,
    @Autowired private val tasks: ParkingPlateKeytopSyncTaskRepository,
    @Autowired private val objectMapper: ObjectMapper,
) {
    @MockitoBean
    lateinit var keytopService: KeytopService

    @MockitoBean
    lateinit var auditService: AuditService

    @BeforeEach
    fun clean() {
        tasks.deleteAll()
        plates.deleteAll()
        owners.deleteAll()
    }

    @Test
    fun `启用车牌时新增月卡并保存月卡与车牌项标识`() {
        val plate = savePlate("京A10001")
        whenever(keytopService.getCarCardInfo("京A10001"))
            .thenReturn(success("{}"), success("""{"cardInfo":{"cardId":8101,"plateNoInfo":"[{\"id\":9101,\"plateNo\":\"京A10001\"}]"}}"""))
        whenever(keytopService.addCarCardNo(any<Long>(), any<String>(), any<KeytopCardInfo>(), any<List<KeytopCarLot>>(), any<List<KeytopPlateNo>>()))
            .thenReturn(success("{}"))
        whenever(keytopService.payCarCardFee(any<KeytopPayCarCardFeeRequest>())).thenReturn(success("{}"))

        service.enqueueAfterChange(plate, null, 0, null)
        assertEquals(1, service.processBatch())

        val saved = plates.findById(requireNotNull(plate.id)).orElseThrow()
        assertEquals(ParkingPlate.KeytopSyncStatus.SYNCED, saved.keytopSyncStatus)
        assertEquals(8101L, saved.keytopCardId)
        assertEquals(9101L, saved.keytopPlateId)
        assertEquals(ParkingPlateKeytopSyncTask.Status.SUCCEEDED, tasks.findAll().single().status)
        verify(keytopService).payCarCardFee(any<KeytopPayCarCardFeeRequest>())
    }

    @Test
    fun `删除车牌时保留撤销任务并幂等删除月卡`() {
        val plate = savePlate("京B10002").apply {
            keytopCardId = 8201L
            keytopPlateId = 9201L
            keytopSyncStatus = ParkingPlate.KeytopSyncStatus.SYNCED
        }.let(plates::save)
        whenever(keytopService.getCarCardInfo(8201L)).thenReturn(
            success("""{"cardInfo":{"cardId":8201,"plateNoInfo":"[{\"id\":9201,\"plateNo\":\"京B10002\"}]"}}"""),
        )
        whenever(keytopService.delCarCardInfo(8201L)).thenReturn(success("{}"))

        service.enqueueDelete(plate)
        plates.delete(plate)
        assertEquals(1, service.processBatch())

        assertNull(plates.findById(requireNotNull(plate.id)).orElse(null))
        val task = tasks.findAll().single()
        assertEquals(ParkingPlateKeytopSyncTask.Operation.DELETE, task.operation)
        assertEquals(ParkingPlateKeytopSyncTask.Status.SUCCEEDED, task.status)
        verify(keytopService).delCarCardInfo(8201L)
        verify(keytopService, never()).addCarCardNo(any<Long>(), any<String>(), any<KeytopCardInfo>(), any<List<KeytopCarLot>>(), any<List<KeytopPlateNo>>())
    }

    private fun savePlate(number: String): ParkingPlate {
        val owner = owners.save(ParkingOwner().apply {
            cardId = "OWNER-$number"
            name = "同步测试车主"
            dept = "测试部门"
            phone = "13800000000"
        })
        return plates.save(ParkingPlate().apply {
            plate = number
            this.owner = owner.name
            ownerId = requireNotNull(owner.id)
            status = 1
            regDate = LocalDate.of(2026, 9, 19)
        })
    }

    private fun success(data: String): KeytopResponse = KeytopResponse(0, "success", objectMapper.readTree(data))
}
