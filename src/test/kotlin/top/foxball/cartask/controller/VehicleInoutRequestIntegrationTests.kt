package top.foxball.cartask.controller

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.context.SecurityContextImpl
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import top.foxball.cartask.audit.AuditAction
import top.foxball.cartask.audit.AuditCommand
import top.foxball.cartask.audit.AuditService
import top.foxball.cartask.authentication.CurrentUserPrincipal
import top.foxball.cartask.entity.AuditEvent
import top.foxball.cartask.entity.Department
import top.foxball.cartask.entity.ParkingOwner
import top.foxball.cartask.entity.ParkingPlate
import top.foxball.cartask.entity.User
import top.foxball.cartask.entity.UserManagedDepartment
import top.foxball.cartask.entity.VehicleInoutRequest
import top.foxball.cartask.entity.type.ZoneType
import top.foxball.cartask.keytop.KeytopCarLot
import top.foxball.cartask.keytop.KeytopCardInfo
import top.foxball.cartask.keytop.KeytopPlateNo
import top.foxball.cartask.keytop.KeytopResponse
import top.foxball.cartask.keytop.KeytopService
import top.foxball.cartask.repository.DepartmentRepository
import top.foxball.cartask.repository.ParkingOwnerRepository
import top.foxball.cartask.repository.ParkingPlateRepository
import top.foxball.cartask.repository.UserManagedDepartmentRepository
import top.foxball.cartask.repository.UserRepository
import top.foxball.cartask.repository.VehicleInoutRequestRepository
import top.foxball.cartask.repository.ZoneTypeRepository
import top.foxball.cartask.scope.WithCurrentUser
import tools.jackson.databind.ObjectMapper
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue











@SpringBootTest(properties = [
    "app.mock-data.enabled=false",
    "spring.task.scheduling.enabled=false",
    "spring.datasource.url=jdbc:h2:mem:vehicle_inout_request_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
])
@ActiveProfiles("test")
class VehicleInoutRequestIntegrationTests(
    @Autowired private val api: VehicleInoutRequestController,
    @Autowired private val requests: VehicleInoutRequestRepository,
    @Autowired private val owners: ParkingOwnerRepository,
    @Autowired private val plates: ParkingPlateRepository,
    @Autowired private val departments: DepartmentRepository,
    @Autowired private val users: UserRepository,
    @Autowired private val managedDepartments: UserManagedDepartmentRepository,
    @Autowired private val zones: ZoneTypeRepository,
    @Autowired private val objectMapper: ObjectMapper,
) {
    @MockitoBean
    lateinit var keytopService: KeytopService

    @MockitoBean
    lateinit var auditService: AuditService

    @AfterEach
    fun clearAuthentication() {
        SecurityContextHolder.clearContext()
    }

    @Test
    @WithCurrentUser(role = "ADMIN", authorities = ["vehicle-inout-request:read", "vehicle-inout-request:apply"])
    fun `登记从车牌档案带出车主信息并把申请置为待审核`() {
        val owner = saveOwner("张三", "13800000101")
        val plate = savePlate(uniquePlate("京A"), owner)

        val created = createRequest(plate.plate)

        assertEquals(owner.name, created.owner)
        assertEquals(owner.phone, created.phone)
        assertEquals(owner.dept, created.dept)
        assertEquals(owner.departmentCode, created.departmentCode)
        assertEquals(plate.id, created.plateId)
        assertEquals(VehicleInoutRequest.Status.PENDING, created.status)
        assertEquals(VehicleInoutRequest.SyncStatus.NOT_SYNCED, created.syncStatus)

        verify(keytopService, never()).addCarCardNo(any<Long>(), any<String>(), any<KeytopCardInfo>(), any<List<KeytopCarLot>>(), any<List<KeytopPlateNo>>())
        verify(keytopService, never()).payCarCardFee(any())
    }

    @Test
    @WithCurrentUser(role = "ADMIN", authorities = ["vehicle-inout-request:apply"])
    fun `车牌不在档案中时拒绝登记且不落库`() {
        val before = requests.count()
        val missing = uniquePlate("京Z")

        val error = assertFailsWith<IllegalArgumentException> { createRequest(missing) }

        assertTrue(error.message!!.contains("不在车牌档案中"))
        assertEquals(before, requests.count())
        assertTrue(requests.findAll().none { it.plate == missing })
    }

    @Test
    @WithCurrentUser(role = "ADMIN", authorities = ["vehicle-inout-request:read", "vehicle-inout-request:apply"])
    fun `同一车牌的未决申请不允许重复登记`() {
        val owner = saveOwner("李四", "13800000102")
        val plate = savePlate(uniquePlate("京B"), owner)
        createRequest(plate.plate)

        val error = assertFailsWith<IllegalArgumentException> { createRequest(plate.plate) }

        assertTrue(error.message!!.contains("已有待处理的进出申请"))
    }

    @Test
    @WithCurrentUser(role = "ADMIN", authorities = ["vehicle-inout-request:read", "vehicle-inout-request:apply", "vehicle-inout-request:review"])
    fun `审核状态机单向且驳回必须给理由`() {
        val owner = saveOwner("王五", "13800000103")
        val plate = savePlate(uniquePlate("京C"), owner)
        val id = requireNotNull(createRequest(plate.plate).id)

        assertTrue(assertFailsWith<IllegalArgumentException> {
            api.review(id, VehicleInoutRequestReviewBody(approved = false, reason = "  "))
        }.message!!.contains("驳回原因不能为空"))

        api.review(id, VehicleInoutRequestReviewBody(approved = true))
        assertEquals(VehicleInoutRequest.Status.APPROVED, reload(id).status)

        assertTrue(assertFailsWith<IllegalArgumentException> {
            api.review(id, VehicleInoutRequestReviewBody(approved = true))
        }.message!!.contains("不允许审核"))
        assertTrue(assertFailsWith<IllegalArgumentException> {
            api.review(id, VehicleInoutRequestReviewBody(approved = false, reason = "反悔"))
        }.message!!.contains("不允许审核"))
    }

    @Test
    @WithCurrentUser(role = "ADMIN", authorities = ["vehicle-inout-request:read", "vehicle-inout-request:apply"])
    fun `已决申请不能编辑撤销后也不能复活`() {
        val owner = saveOwner("赵六", "13800000104")
        val plate = savePlate(uniquePlate("京D"), owner)
        val id = requireNotNull(createRequest(plate.plate).id)

        api.cancel(id, VehicleInoutRequestCancelBody("不再需要"))
        assertEquals(VehicleInoutRequest.Status.CANCELLED, reload(id).status)

        assertTrue(assertFailsWith<IllegalArgumentException> {
            api.update(id, VehicleInoutRequestUpdateBody().apply { cardName = "改个名字" })
        }.message!!.contains("只有待审核的申请可以编辑"))
        assertTrue(assertFailsWith<IllegalArgumentException> { api.cancel(id, VehicleInoutRequestCancelBody()) }
            .message!!.contains("不允许撤销"))
    }

    @Test
    @WithCurrentUser(role = "ADMIN", authorities = ["vehicle-inout-request:read", "vehicle-inout-request:apply", "vehicle-inout-request:review", "vehicle-inout-request:sync"])
    fun `新增成功但未读回卡号时重试不会再次新增月卡`() {
        val owner = saveOwner("钱七", "13800000105")
        val plate = savePlate(uniquePlate("京E"), owner)
        val id = requireNotNull(createRequest(plate.plate).id)
        api.review(id, VehicleInoutRequestReviewBody(approved = true))
        whenever(keytopService.getCarCardInfo(plate.plate)).thenReturn(success("""{"cardInfo":{}}"""))
        whenever(keytopService.addCarCardNo(any<Long>(), any<String>(), any<KeytopCardInfo>(), any<List<KeytopCarLot>>(), any<List<KeytopPlateNo>>())).thenReturn(success("{}"))

        api.synchronize(id)

        val failed = reload(id)
        assertEquals(VehicleInoutRequest.SyncStatus.FAILED, failed.syncStatus)
        assertTrue(failed.cardIssued)
        assertNull(failed.cardId)

        api.synchronize(id)

        verify(keytopService, times(1))
            .addCarCardNo(any<Long>(), any<String>(), any<KeytopCardInfo>(), any<List<KeytopCarLot>>(), any<List<KeytopPlateNo>>())
        assertTrue(reload(id).syncMessage!!.contains("不再重复新增"))
        assertEquals(VehicleInoutRequest.SyncStatus.FAILED, reload(id).syncStatus)
    }

    @Test
    @WithCurrentUser(role = "ADMIN", authorities = ["vehicle-inout-request:read", "vehicle-inout-request:apply", "vehicle-inout-request:review", "vehicle-inout-request:sync"])
    fun `下发成功后再写审计失败也不会把结论回滚成失败`() {
        val owner = saveOwner("卫十九", "13800000121")
        val plate = savePlate(uniquePlate("京T"), owner)
        val id = requireNotNull(createRequest(plate.plate).id)
        api.review(id, VehicleInoutRequestReviewBody(approved = true))
        whenever(keytopService.getCarCardInfo(plate.plate)).thenReturn(success("""{"cardInfo":{"cardId":6100}}"""))
        whenever(keytopService.modifyCarCardNo(any<Long>(), any<String>(), any<KeytopCardInfo>(), any<List<KeytopCarLot>>(), any<List<KeytopPlateNo>>())).thenReturn(success("{}"))
        whenever(keytopService.payCarCardFee(any())).thenReturn(success("{}"))
        whenever(auditService.record(any())).thenThrow(IllegalStateException("审计库不可用"))

        val outcome = api.synchronize(id)

        assertEquals("月卡已下发", outcome.body!!.message)
        val saved = reload(id)
        assertEquals(VehicleInoutRequest.SyncStatus.SYNCED, saved.syncStatus)
        assertEquals(6100L, saved.cardId)

        assertTrue(assertFailsWith<IllegalArgumentException> { api.synchronize(id) }
            .message!!.contains("无需重复下发"))
        verify(keytopService, times(1)).payCarCardFee(any())
    }

    @Test
    @WithCurrentUser(role = "ADMIN", authorities = ["vehicle-inout-request:read", "vehicle-inout-request:apply", "vehicle-inout-request:review", "vehicle-inout-request:sync"])
    fun `同一车牌换个写法也不能重复登记`() {
        val owner = saveOwner("王五", "13800000103")
        val index = SEQUENCE.incrementAndGet()
        val archived = "京C·${index.toString().padStart(5, '0')}"
        val plain = "京C${index.toString().padStart(5, '0')}"
        savePlate(archived, owner)
        createRequest(archived)

        val error = assertFailsWith<IllegalArgumentException> { createRequest(plain) }

        assertTrue(error.message!!.contains("已有待处理的进出申请"))
    }

    @Test
    @WithCurrentUser(role = "ADMIN", authorities = ["vehicle-inout-request:read", "vehicle-inout-request:apply", "vehicle-inout-request:review", "vehicle-inout-request:sync"])
    fun `平台无月卡时新增并缴费且回填卡ID`() {
        val owner = saveOwner("钱七", "13800000105")
        val plate = savePlate(uniquePlate("京E"), owner)
        val id = requireNotNull(createRequest(plate.plate).id)
        api.review(id, VehicleInoutRequestReviewBody(approved = true))
        whenever(keytopService.getCarCardInfo(plate.plate))
            .thenReturn(success("""{"cardInfo":{}}"""), success("""{"cardInfo":{"cardId":8801}}"""))
        whenever(keytopService.addCarCardNo(any<Long>(), any<String>(), any<KeytopCardInfo>(), any<List<KeytopCarLot>>(), any<List<KeytopPlateNo>>())).thenReturn(success("{}"))
        whenever(keytopService.payCarCardFee(any())).thenReturn(success("{}"))

        val outcome = api.synchronize(id).body!!

        assertEquals("月卡已下发", outcome.message)
        verify(keytopService).addCarCardNo(any<Long>(), any<String>(), any<KeytopCardInfo>(), any<List<KeytopCarLot>>(), any<List<KeytopPlateNo>>())
        verify(keytopService).payCarCardFee(any())
        verify(keytopService, never()).modifyCarCardNo(any<Long>(), any<String>(), any<KeytopCardInfo>(), any<List<KeytopCarLot>>(), any<List<KeytopPlateNo>>())

        val saved = reload(id)
        assertEquals(VehicleInoutRequest.SyncStatus.SYNCED, saved.syncStatus)
        assertEquals(8801L, saved.cardId)
        assertNull(saved.syncMessage)
        assertNotNull(saved.syncedAt)
    }

    @Test
    @WithCurrentUser(role = "ADMIN", authorities = ["vehicle-inout-request:read", "vehicle-inout-request:apply", "vehicle-inout-request:review", "vehicle-inout-request:sync"])
    fun `平台已有月卡时只改有效期不重复发卡`() {
        val owner = saveOwner("孙八", "13800000106")
        val plate = savePlate(uniquePlate("京F"), owner)
        val id = requireNotNull(createRequest(plate.plate).id)
        api.review(id, VehicleInoutRequestReviewBody(approved = true))
        whenever(keytopService.getCarCardInfo(plate.plate)).thenReturn(success("""{"cardInfo":{"cardId":9007}}"""))
        whenever(keytopService.modifyCarCardNo(any<Long>(), any<String>(), any<KeytopCardInfo>(), any<List<KeytopCarLot>>(), any<List<KeytopPlateNo>>())).thenReturn(success("{}"))
        whenever(keytopService.payCarCardFee(any())).thenReturn(success("{}"))

        api.synchronize(id)

        verify(keytopService, never()).addCarCardNo(any<Long>(), any<String>(), any<KeytopCardInfo>(), any<List<KeytopCarLot>>(), any<List<KeytopPlateNo>>())
        verify(keytopService).modifyCarCardNo(any<Long>(), any<String>(), any<KeytopCardInfo>(), any<List<KeytopCarLot>>(), any<List<KeytopPlateNo>>())
        verify(keytopService).payCarCardFee(any())
        assertEquals(9007L, reload(id).cardId)
    }

    @Test
    @WithCurrentUser(role = "ADMIN", authorities = ["vehicle-inout-request:read", "vehicle-inout-request:apply", "vehicle-inout-request:review", "vehicle-inout-request:sync"])
    fun `下发失败保留审批结论并记录可重试的失败原因`() {
        val owner = saveOwner("周九", "13800000107")
        val plate = savePlate(uniquePlate("京G"), owner)
        val id = requireNotNull(createRequest(plate.plate).id)
        api.review(id, VehicleInoutRequestReviewBody(approved = true))
        whenever(keytopService.getCarCardInfo(plate.plate)).thenReturn(KeytopResponse(500, "平台不可用", null))

        val outcome = api.synchronize(id).body!!

        assertEquals("月卡下发失败", outcome.message)
        val saved = reload(id)
        assertEquals(VehicleInoutRequest.Status.APPROVED, saved.status)
        assertEquals(VehicleInoutRequest.SyncStatus.FAILED, saved.syncStatus)
        assertTrue(saved.syncMessage!!.contains("平台不可用"))

        val captor = argumentCaptor<AuditCommand>()
        verify(auditService, atLeastOnce()).record(captor.capture())
        val failure = captor.allValues.last { it.action == AuditAction.VEHICLE_INOUT_REQUEST_SYNCED }
        assertEquals(AuditEvent.Result.FAILED, failure.result)
        assertFalse(failure.targetSummary.isNullOrEmpty(), "审计摘要不能为空：${failure.targetSummary}")
    }

    @Test
    @WithCurrentUser(role = "ADMIN", authorities = ["vehicle-inout-request:read", "vehicle-inout-request:apply", "vehicle-inout-request:review", "vehicle-inout-request:sync"])
    fun `缴费失败时已新增的卡ID仍被保留以免重试多发卡`() {
        val owner = saveOwner("吴十", "13800000108")
        val plate = savePlate(uniquePlate("京H"), owner)
        val id = requireNotNull(createRequest(plate.plate).id)
        api.review(id, VehicleInoutRequestReviewBody(approved = true))
        whenever(keytopService.addCarCardNo(any<Long>(), any<String>(), any<KeytopCardInfo>(), any<List<KeytopCarLot>>(), any<List<KeytopPlateNo>>())).thenReturn(success("{}"))
        whenever(keytopService.payCarCardFee(any())).thenReturn(KeytopResponse(1, "缴费失败", null))
        whenever(keytopService.getCarCardInfo(plate.plate))
            .thenReturn(success("""{"cardInfo":{}}"""), success("""{"cardInfo":{"cardId":7712}}"""))

        api.synchronize(id)

        val failed = reload(id)
        assertEquals(VehicleInoutRequest.SyncStatus.FAILED, failed.syncStatus)
        assertEquals(7712L, failed.cardId)

        whenever(keytopService.modifyCarCardNo(any<Long>(), any<String>(), any<KeytopCardInfo>(), any<List<KeytopCarLot>>(), any<List<KeytopPlateNo>>())).thenReturn(success("{}"))
        whenever(keytopService.payCarCardFee(any())).thenReturn(success("{}"))
        assertEquals("月卡已下发", api.synchronize(id).body!!.message)

        verify(keytopService).addCarCardNo(any<Long>(), any<String>(), any<KeytopCardInfo>(), any<List<KeytopCarLot>>(), any<List<KeytopPlateNo>>())
        verify(keytopService).modifyCarCardNo(any<Long>(), any<String>(), any<KeytopCardInfo>(), any<List<KeytopCarLot>>(), any<List<KeytopPlateNo>>())
        assertEquals(VehicleInoutRequest.SyncStatus.SYNCED, reload(id).syncStatus)
    }

    @Test
    @WithCurrentUser(role = "ADMIN", authorities = ["vehicle-inout-request:read", "vehicle-inout-request:apply"])
    fun `停车区域不在字典中时拒绝登记`() {
        val owner = saveOwner("沈十七", "13800000119")
        val plate = savePlate(uniquePlate("京R"), owner)

        val error = assertFailsWith<IllegalArgumentException> {
            api.create(
                VehicleInoutRequestCreateBody(
                    plate = plate.plate,
                    cardName = "内部员工月卡",
                    areaCode = "NOT-A-ZONE-${SEQUENCE.incrementAndGet()}",
                    validFrom = "2026-09-01T00:00",
                    validTo = "2026-10-01T23:59",
                ),
            )
        }

        assertTrue(error.message!!.contains("不在停车区域字典中"))
        assertTrue(requests.findAll().none { it.plate == plate.plate })
    }

    @Test
    @WithCurrentUser(role = "ADMIN", authorities = ["vehicle-inout-request:read", "vehicle-inout-request:apply"])
    fun `编辑时传空区域可以清空已选的停车区域`() {
        val owner = saveOwner("韩十八", "13800000120")
        val plate = savePlate(uniquePlate("京S"), owner)
        val zoneCode = "VIN-ZONE-${SEQUENCE.incrementAndGet()}"
        zones.save(ZoneType().apply {
            this.zoneCode = zoneCode
            zoneName = "申请测试区"
            orderNumber = 1
            placeCount = 10
            createdAt = LocalDateTime.now()
            updatedAt = createdAt
        })
        api.create(
            VehicleInoutRequestCreateBody(
                plate = plate.plate,
                cardName = "内部员工月卡",
                areaCode = zoneCode,
                validFrom = "2026-09-01T00:00",
                validTo = "2026-10-01T23:59",
            ),
        )
        val id = requireNotNull(requests.findAll().firstOrNull { it.plate == plate.plate }?.id)
        assertEquals("申请测试区", reload(id).areaName)

        api.update(id, VehicleInoutRequestUpdateBody())
        assertEquals(zoneCode, reload(id).areaCode)

        api.update(id, VehicleInoutRequestUpdateBody().apply { areaCode = null; areaCodeProvided = true })
        val cleared = reload(id)
        assertNull(cleared.areaCode)
        assertNull(cleared.areaName)
    }

    @Test
    @WithCurrentUser(role = "ADMIN", authorities = ["vehicle-inout-request:read", "vehicle-inout-request:apply"])
    fun `登记审计字段落在白名单内且同步状态为未下发`() {
        val owner = saveOwner("褚十四", "13800000114")
        val plate = savePlate(uniquePlate("京O"), owner)

        val created = api.create(
            VehicleInoutRequestCreateBody(
                plate = plate.plate,
                cardName = "内部员工月卡",
                validFrom = "2026-09-01T00:00",
                validTo = "2026-10-01T23:59",
            ),
        )

        assertEquals(201, created.statusCode.value())
    }

    @Test
    @WithCurrentUser(role = "ADMIN", authorities = ["vehicle-inout-request:read", "vehicle-inout-request:apply", "vehicle-inout-request:review", "vehicle-inout-request:sync"])
    fun `车牌档案带间隔符时按归一化车牌下发`() {
        val owner = saveOwner("卫十五", "13800000117")
        val index = SEQUENCE.incrementAndGet()
        val archived = "京P·${index.toString().padStart(5, '0')}"
        val normalized = "京P${index.toString().padStart(5, '0')}"
        savePlate(archived, owner)
        val id = requireNotNull(createRequest(archived).id)
        api.review(id, VehicleInoutRequestReviewBody(approved = true))
        whenever(keytopService.getCarCardInfo(normalized)).thenReturn(success("""{"cardInfo":{"cardId":4200}}"""))
        whenever(keytopService.modifyCarCardNo(any<Long>(), any<String>(), any<KeytopCardInfo>(), any<List<KeytopCarLot>>(), any<List<KeytopPlateNo>>())).thenReturn(success("{}"))
        whenever(keytopService.payCarCardFee(any())).thenReturn(success("{}"))

        assertEquals("月卡已下发", api.synchronize(id).body!!.message)

        verify(keytopService, never()).getCarCardInfo(archived)
        verify(keytopService).getCarCardInfo(normalized)
    }

    @Test
    @WithCurrentUser(role = "ADMIN", authorities = ["vehicle-inout-request:read", "vehicle-inout-request:apply", "vehicle-inout-request:review", "vehicle-inout-request:sync"])
    fun `下发成功写审计且载荷不为空`() {
        val owner = saveOwner("蒋十六", "13800000118")
        val plate = savePlate(uniquePlate("京Q"), owner)
        val id = requireNotNull(createRequest(plate.plate).id)
        api.review(id, VehicleInoutRequestReviewBody(approved = true))
        whenever(keytopService.getCarCardInfo(plate.plate)).thenReturn(success("""{"cardInfo":{"cardId":5500}}"""))
        whenever(keytopService.modifyCarCardNo(any<Long>(), any<String>(), any<KeytopCardInfo>(), any<List<KeytopCarLot>>(), any<List<KeytopPlateNo>>())).thenReturn(success("{}"))
        whenever(keytopService.payCarCardFee(any())).thenReturn(success("{}"))

        api.synchronize(id)

        val captor = argumentCaptor<AuditCommand>()
        verify(auditService, atLeastOnce()).record(captor.capture())
        val synced = captor.allValues.last { it.action == AuditAction.VEHICLE_INOUT_REQUEST_SYNCED }
        assertEquals(AuditEvent.Result.SUCCESS, synced.result)
        assertFalse(synced.targetSummary.isNullOrEmpty(), "下发摘要不能为空：${synced.targetSummary}")
        assertTrue(synced.targetSummary!!.containsKey("card_id"), "摘要里应记下卡 ID：${synced.targetSummary}")
        assertEquals(true, synced.afterData?.get("synchronized"))
    }

    @Test
    @WithCurrentUser(role = "ADMIN", authorities = ["vehicle-inout-request:read", "vehicle-inout-request:apply"])
    fun `有效期结束早于开始时拒绝登记`() {
        val owner = saveOwner("沈十七", "13800000119")
        val plate = savePlate(uniquePlate("京R"), owner)

        val error = assertFailsWith<IllegalArgumentException> {
            api.create(
                VehicleInoutRequestCreateBody(
                    plate = plate.plate,
                    cardName = "内部员工月卡",
                    validFrom = "2026-10-01T00:00",
                    validTo = "2026-09-01T00:00",
                ),
            )
        }

        assertTrue(error.message!!.contains("不能早于开始时间"))
        assertTrue(requests.findAll().none { it.plate == plate.plate })
    }

    @Test
    @WithCurrentUser(role = "ADMIN", authorities = ["vehicle-inout-request:read", "vehicle-inout-request:apply"])
    fun `停车区域按字典解析出名称快照`() {
        val owner = saveOwner("韩十八", "13800000120")
        val plate = savePlate(uniquePlate("京S"), owner)
        val zoneCode = "VIN-ZONE-${SEQUENCE.incrementAndGet()}"
        zones.save(ZoneType().apply {
            this.zoneCode = zoneCode
            zoneName = "申请测试区"
            orderNumber = 1
            placeCount = 10
            createdAt = LocalDateTime.now()
            updatedAt = createdAt
        })

        val created = api.create(
            VehicleInoutRequestCreateBody(
                plate = plate.plate,
                cardName = "内部员工月卡",
                areaCode = zoneCode,
                validFrom = "2026-09-01T00:00",
                validTo = "2026-10-01T23:59",
            ),
        )
        val saved = requireNotNull(requests.findAll().firstOrNull { it.plate == plate.plate })

        assertEquals(zoneCode, saved.areaCode)
        assertEquals("申请测试区", saved.areaName)
        assertEquals(201, created.statusCode.value())
    }

    @Test
    @WithCurrentUser(role = "ADMIN", authorities = ["vehicle-inout-request:read", "vehicle-inout-request:apply", "vehicle-inout-request:review", "vehicle-inout-request:sync"])
    fun `已下发的申请不能重复下发也不能撤销`() {
        val owner = saveOwner("郑十一", "13800000109")
        val plate = savePlate(uniquePlate("京I"), owner)
        val id = requireNotNull(createRequest(plate.plate).id)
        api.review(id, VehicleInoutRequestReviewBody(approved = true))
        whenever(keytopService.getCarCardInfo(plate.plate)).thenReturn(success("""{"cardInfo":{"cardId":6001}}"""))
        whenever(keytopService.modifyCarCardNo(any<Long>(), any<String>(), any<KeytopCardInfo>(), any<List<KeytopCarLot>>(), any<List<KeytopPlateNo>>())).thenReturn(success("{}"))
        whenever(keytopService.payCarCardFee(any())).thenReturn(success("{}"))
        api.synchronize(id)

        assertTrue(assertFailsWith<IllegalArgumentException> { api.synchronize(id) }
            .message!!.contains("无需重复下发"))
        assertTrue(assertFailsWith<IllegalArgumentException> { api.cancel(id, VehicleInoutRequestCancelBody("试试")) }
            .message!!.contains("不能撤销"))
    }

    @Test
    @WithCurrentUser(role = "ADMIN", authorities = ["vehicle-inout-request:read", "vehicle-inout-request:apply", "vehicle-inout-request:review", "vehicle-inout-request:sync"])
    fun `未审核通过与不存在的申请都不能下发`() {
        val owner = saveOwner("冯十二", "13800000110")
        val plate = savePlate(uniquePlate("京J"), owner)
        val id = requireNotNull(createRequest(plate.plate).id)

        assertTrue(assertFailsWith<IllegalArgumentException> { api.synchronize(id) }
            .message!!.contains("只有已通过的申请可以下发给科拓"))
        assertTrue(assertFailsWith<IllegalArgumentException> { api.synchronize(999_999L) }
            .message!!.contains("申请单不存在"))
        verify(keytopService, never()).addCarCardNo(any<Long>(), any<String>(), any<KeytopCardInfo>(), any<List<KeytopCarLot>>(), any<List<KeytopPlateNo>>())
        verify(keytopService, never()).payCarCardFee(any())
        assertEquals(VehicleInoutRequest.Status.PENDING, reload(id).status)
    }

    @Test
    @WithCurrentUser(role = "ADMIN", authorities = ["vehicle-inout-request:read", "vehicle-inout-request:apply", "vehicle-inout-request:review"])
    fun `审核审计能区分通过与驳回且载荷不为空`() {
        val owner = saveOwner("陈十三", "13800000111")
        val approved = requireNotNull(createRequest(savePlate(uniquePlate("京K"), owner).plate).id)
        val rejected = requireNotNull(createRequest(savePlate(uniquePlate("京L"), owner).plate).id)

        api.review(approved, VehicleInoutRequestReviewBody(approved = true))
        api.review(rejected, VehicleInoutRequestReviewBody(approved = false, reason = "车牌与档案不符"))

        val captor = argumentCaptor<AuditCommand>()
        verify(auditService, atLeastOnce()).record(captor.capture())
        val reviews = captor.allValues.filter { it.action == AuditAction.VEHICLE_INOUT_REQUEST_REVIEWED }
        assertEquals(2, reviews.size)

        reviews.forEach { command ->
            assertFalse(command.beforeData.isNullOrEmpty(), "审核前状态不能为空：${command.beforeData}")
            assertFalse(command.afterData.isNullOrEmpty(), "审核后状态不能为空：${command.afterData}")
            assertFalse(command.targetSummary.isNullOrEmpty(), "审计摘要不能为空：${command.targetSummary}")
            assertTrue(command.targetSummary!!.containsKey("plate"), "摘要里应记下车牌：${command.targetSummary}")
        }
        assertEquals(
            setOf(VehicleInoutRequest.Status.APPROVED.value(), VehicleInoutRequest.Status.REJECTED.value()),
            reviews.map { it.afterData?.get("review_status") }.toSet(),
        )
        assertTrue(reviews.any { it.reason == "车牌与档案不符" })
    }

    @Test
    @WithCurrentUser(role = "ADMIN", authorities = ["vehicle-inout-request:read", "vehicle-inout-request:apply"])
    fun `登记写审计且载荷不为空`() {
        val owner = saveOwner("褚十四", "13800000114")
        val plate = savePlate(uniquePlate("京O"), owner)

        createRequest(plate.plate)

        val captor = argumentCaptor<AuditCommand>()
        verify(auditService, atLeastOnce()).record(captor.capture())
        val created = captor.allValues.last { it.action == AuditAction.VEHICLE_INOUT_REQUEST_CREATED }
        assertFalse(created.targetSummary.isNullOrEmpty(), "登记摘要不能为空：${created.targetSummary}")
        assertFalse(created.afterData.isNullOrEmpty(), "登记后状态不能为空：${created.afterData}")
    }

    @Test
    fun `列表与写路径按工作部门裁剪且不能越权登记他人部门的车牌`() {
        val scope = departmentAdminScope()
        val permissions = setOf(
            "vehicle-inout-request:read",
            "vehicle-inout-request:apply",
            "vehicle-inout-request:review",
            "vehicle-inout-request:sync",
        )

        authenticate(scope.userId, "ADMIN", permissions)
        val mine = savePlate(uniquePlate("京M"), saveOwner("本部门车主", "13800000115", scope.departmentCode))
        val other = savePlate(uniquePlate("京N"), saveOwner("他部门车主", "13800000116", "VIN-SCOPE-B"))

        authenticate(scope.userId, "DEPT_ADMIN", permissions)
        val mineId = requireNotNull(createRequest(mine.plate).id)

        assertTrue(assertFailsWith<IllegalArgumentException> { createRequest(other.plate) }
            .message!!.contains("车牌不存在"))

        authenticate(scope.userId, "ADMIN", permissions)
        val otherId = requireNotNull(createRequest(other.plate).id)
        api.review(otherId, VehicleInoutRequestReviewBody(approved = true))
        authenticate(scope.userId, "DEPT_ADMIN", permissions)

        assertEquals(listOf(mineId), listedIds())
        assertTrue(assertFailsWith<IllegalArgumentException> { api.get(otherId) }
            .message!!.contains("申请单不存在"))
        assertTrue(assertFailsWith<IllegalArgumentException> {
            api.review(otherId, VehicleInoutRequestReviewBody(approved = true))
        }.message!!.contains("申请单不存在"))
        assertTrue(assertFailsWith<IllegalArgumentException> { api.synchronize(otherId) }
            .message!!.contains("申请单不存在"))
        verify(keytopService, never())
            .addCarCardNo(any<Long>(), any<String>(), any<KeytopCardInfo>(), any<List<KeytopCarLot>>(), any<List<KeytopPlateNo>>())
        assertEquals(VehicleInoutRequest.SyncStatus.NOT_SYNCED, reload(otherId).syncStatus)
    }

    private fun createWithNewOwner(plate: String, name: String, phone: String, departmentId: Long?): VehicleInoutRequest {
        api.create(
            VehicleInoutRequestCreateBody(
                plate = plate,
                cardName = "内部员工月卡",
                validFrom = "2026-09-01T00:00",
                validTo = "2026-10-01T23:59",
                newOwner = VehicleInoutRequestNewOwnerBody(
                    name = name,
                    phone = phone,
                    departmentId = departmentId,
                    jobTitle = "工程师",
                ),
            ),
        )
        return requests.findAll().single { it.plate == plate }
    }

    private fun saveDepartment(): Department {
        val index = SEQUENCE.incrementAndGet()
        return departments.save(
            Department().apply {
                name = "建档测试部门$index"
                departmentNumber = "VIN-ARCHIVE-$index"
                sortOrder = index
                status = 1
            },
        )
    }

    private fun newUser(username: String, department: Department): User {
        val now = LocalDateTime.now()
        return User().apply {
            this.username = username
            nickName = "占位账号"
            email = "$username@local.invalid"
            passwordHash = "x"
            role = "USER"
            this.department = department
            createdAt = now
            updatedAt = now
        }
    }

    private fun uniquePhone(): String = "139" + SEQUENCE.incrementAndGet().toString().padStart(8, '0')

    @Test
    @WithCurrentUser(
        role = "ADMIN",
        authorities = ["vehicle-inout-request:read", "vehicle-inout-request:apply", "user:create", "owner:manage", "plate:manage"],
    )
    fun `登记时顺带新建车主账号与车牌`() {
        val department = saveDepartment()
        val plate = uniquePlate("京U")
        val phone = uniquePhone()

        createWithNewOwner(plate, "新车主", phone, department.id)

        val user = requireNotNull(users.findByUsername(phone)) { "顺带建档未创建账号" }
        assertEquals("新车主", user.nickName)
        assertEquals("工程师", user.jobTitle)
        assertEquals(department.id, user.department?.id)
        assertTrue(user.mustChangePassword, "初始密码属于公开信息，必须强制首次登录改密")
        assertEquals("USER", user.role)

        val owner = owners.findByPhone(phone).single()
        assertEquals(department.departmentNumber, owner.departmentCode)
        assertEquals(department.name, owner.dept)
        assertEquals(user.id, owner.linkedUserId)
        assertTrue(owner.cardId.startsWith("AUTO-"), "车主卡号应自动生成：${owner.cardId}")

        val savedPlate = requireNotNull(plates.findByPlate(plate))
        assertEquals(owner.id, savedPlate.ownerId)

        val request = requests.findAll().single { it.plate == plate }
        assertEquals(phone, request.phone)
        assertEquals(department.departmentNumber, request.departmentCode)
        assertEquals(owner.id, request.ownerId)
    }

    @Test
    @WithCurrentUser(
        role = "ADMIN",
        authorities = ["vehicle-inout-request:read", "vehicle-inout-request:apply", "user:create", "owner:manage", "plate:manage"],
    )
    fun `自设密码时不强制改密且审计记下开通的账号`() {
        val department = saveDepartment()
        val plate = uniquePlate("京V")
        val phone = uniquePhone()

        api.create(
            VehicleInoutRequestCreateBody(
                plate = plate,
                cardName = "内部员工月卡",
                validFrom = "2026-09-01T00:00",
                validTo = "2026-10-01T23:59",
                newOwner = VehicleInoutRequestNewOwnerBody(
                    name = "自设密码车主",
                    phone = phone,
                    departmentId = department.id,
                    jobTitle = "工程师",
                    password = "Operator-Set-Password-1",
                ),
            ),
        )

        assertFalse(requireNotNull(users.findByUsername(phone)).mustChangePassword)

        val captor = argumentCaptor<AuditCommand>()
        verify(auditService, atLeastOnce()).record(captor.capture())
        val created = captor.allValues.last { it.action == AuditAction.VEHICLE_INOUT_REQUEST_CREATED }
        assertEquals(phone, created.targetSummary?.get("created_account"))
    }

    @Test
    @WithCurrentUser(role = "ADMIN", authorities = ["vehicle-inout-request:read", "vehicle-inout-request:apply"])
    fun `只有申请权限时不能顺带建档且不落任何数据`() {
        val department = saveDepartment()
        val plate = uniquePlate("京W")
        val phone = uniquePhone()
        val before = requests.count()

        assertFailsWith<AccessDeniedException> { createWithNewOwner(plate, "越权车主", phone, department.id) }

        assertNull(users.findByUsername(phone))
        assertTrue(owners.findByPhone(phone).isEmpty())
        assertNull(plates.findByPlate(plate))
        assertEquals(before, requests.count())
    }

    @Test
    @WithCurrentUser(
        role = "ADMIN",
        authorities = ["vehicle-inout-request:read", "vehicle-inout-request:apply", "user:create", "owner:manage", "plate:manage"],
    )
    fun `顺带建档拒绝已存在的车牌手机号或账号`() {
        val department = saveDepartment()

        val existingPlate = savePlate(uniquePlate("京X"), saveOwner("既有车主", uniquePhone()))
        assertTrue(assertFailsWith<IllegalArgumentException> {
            createWithNewOwner(existingPlate.plate, "另一个车主", uniquePhone(), department.id)
        }.message!!.contains("已建档"))

        val takenPhone = uniquePhone()
        saveOwner("占用车主", takenPhone)
        assertTrue(assertFailsWith<IllegalArgumentException> {
            createWithNewOwner(uniquePlate("京Y"), "重号车主", takenPhone, department.id)
        }.message!!.contains("已有车主档案"))

        val accountPhone = uniquePhone()
        users.save(newUser(accountPhone, department))
        assertTrue(assertFailsWith<IllegalArgumentException> {
            createWithNewOwner(uniquePlate("京Y"), "重号账号", accountPhone, department.id)
        }.message!!.contains("已注册平台账号"))
    }

    @Test
    fun `部门管理不能把顺带建档挂到范围外的部门`() {
        val scope = departmentAdminScope()
        val ownDepartment = requireNotNull(departments.findAll().firstOrNull { it.departmentNumber == scope.departmentCode })
        val foreignDepartment = saveDepartment()
        authenticate(
            scope.userId,
            "DEPT_ADMIN",
            setOf(
                "vehicle-inout-request:read",
                "vehicle-inout-request:apply",
                "user:create",
                "owner:manage",
                "plate:manage",
            ),
        )

        val phone = uniquePhone()
        assertFailsWith<AccessDeniedException> { createWithNewOwner(uniquePlate("京Z"), "跨部门车主", phone, foreignDepartment.id) }
        assertNull(users.findByUsername(phone))

        createWithNewOwner(uniquePlate("京Z"), "本部门车主", phone, ownDepartment.id)
        assertEquals(ownDepartment.departmentNumber, owners.findByPhone(phone).single().departmentCode)
    }

    @Test
    @WithCurrentUser(
        role = "ADMIN",
        authorities = ["vehicle-inout-request:read", "vehicle-inout-request:apply", "user:create", "owner:manage", "plate:manage"],
    )
    fun `车主姓名超过账号昵称列宽时拒绝登记`() {
        val department = saveDepartment()
        val plate = uniquePlate("京T")
        val tooLong = "长".repeat(65)

        assertTrue(assertFailsWith<IllegalArgumentException> {
            createWithNewOwner(plate, tooLong, uniquePhone(), department.id)
        }.message!!.contains("姓名长度不能超过"))

        assertNull(plates.findByPlate(plate))
        assertTrue(requests.findAll().none { it.plate == plate })
    }

    @Test
    @WithCurrentUser(
        role = "ADMIN",
        authorities = ["vehicle-inout-request:read", "vehicle-inout-request:apply", "user:create", "owner:manage", "plate:manage"],
    )
    fun `后续校验失败时顺带建的三条档案一起回滚`() {
        val department = saveDepartment()
        val plate = uniquePlate("京S")
        val phone = uniquePhone()

        assertFailsWith<IllegalArgumentException> {
            api.create(
                VehicleInoutRequestCreateBody(
                    plate = plate,
                    cardName = "内部员工月卡",
                    validFrom = "2026-10-01T00:00",
                    validTo = "2026-09-01T00:00",
                    newOwner = VehicleInoutRequestNewOwnerBody(
                        name = "回滚车主",
                        phone = phone,
                        departmentId = department.id,
                        jobTitle = "工程师",
                    ),
                ),
            )
        }

        assertNull(users.findByUsername(phone), "账号必须随申请单一起回滚")
        assertTrue(owners.findByPhone(phone).isEmpty(), "车主档案必须随申请单一起回滚")
        assertNull(plates.findByPlate(plate), "车牌档案必须随申请单一起回滚")
    }

    private fun createRequest(plate: String): VehicleInoutRequest {
        api.create(
            VehicleInoutRequestCreateBody(
                plate = plate,
                cardName = "内部员工月卡",
                validFrom = "2026-09-01T00:00",
                validTo = "2026-10-01T23:59",
            ),
        )
        return requireNotNull(requests.findAll().firstOrNull { it.plate == plate }) { "登记未落库：$plate" }
    }

    private fun listedIds(): List<Long> {
        val data = api.list(null, null, null, null, null, 1, 100).body!!.data
        val json = objectMapper.writeValueAsString(data)
        val items = objectMapper.readTree(json).get("items") ?: return emptyList()
        val ids = mutableListOf<Long>()
        items.forEach { node -> ids.add(requireNotNull(node.get("id")).asLong()) }
        return ids
    }

    private fun reload(id: Long): VehicleInoutRequest = requests.findById(id).orElseThrow()

    private fun success(data: String): KeytopResponse = KeytopResponse(0, "success", objectMapper.readTree(data))

    private fun saveOwner(name: String, phone: String, departmentCode: String? = null): ParkingOwner {
        val index = SEQUENCE.incrementAndGet()
        val department = departmentCode ?: DEFAULT_DEPARTMENT_CODE
        val departmentName = departments.findAll().firstOrNull { it.departmentNumber == department }?.name ?: department
        return owners.save(ParkingOwner().apply {
            cardId = "VIN-CARD-$index"
            this.name = "$name-$index"
            dept = departmentName
            this.departmentCode = department
            this.phone = phone
            createdAt = LocalDateTime.now()
            updatedAt = createdAt
        })
    }

    private fun savePlate(plate: String, owner: ParkingOwner): ParkingPlate = plates.save(ParkingPlate().apply {
        this.plate = plate
        this.owner = owner.name
        ownerId = requireNotNull(owner.id)
        regDate = LocalDate.now()
        createdAt = LocalDateTime.now()
        updatedAt = createdAt
    })

    
    private fun uniquePlate(prefix: String): String {
        val index = SEQUENCE.incrementAndGet()
        return "$prefix${index.toString().padStart(5, '0')}"
    }

    private data class DepartmentAdminScope(val userId: Long, val departmentCode: String, val departmentName: String)






    private fun departmentAdminScope(): DepartmentAdminScope {
        val department = departments.findAll().firstOrNull { it.departmentNumber == SCOPE_CODE }
            ?: departments.save(Department().apply { name = "申请范围甲部"; departmentNumber = SCOPE_CODE })
        val user = users.findAll().firstOrNull { it.username == SCOPE_ADMIN_USERNAME }
            ?: users.save(User().apply {
                username = SCOPE_ADMIN_USERNAME
                email = "vin-scope-admin@local.invalid"
                passwordHash = "x"
                role = "DEPT_ADMIN"
                this.department = department
                val now = LocalDateTime.now()
                createdAt = now
                updatedAt = now
            })
        val userId = requireNotNull(user.id)
        if (managedDepartments.findByUserId(userId).isEmpty()) {
            managedDepartments.save(UserManagedDepartment().apply {
                this.user = user
                this.department = department
            })
        }
        return DepartmentAdminScope(userId, SCOPE_CODE, department.name)
    }

    private fun authenticate(userId: Long, role: String, permissions: Set<String>) {
        val principal = CurrentUserPrincipal(
            userId = userId,
            username = "vin-scope-admin",
            role = role,
            tokenId = "test-token",
            permissions = permissions,
        )
        SecurityContextHolder.setContext(
            SecurityContextImpl(UsernamePasswordAuthenticationToken(principal, null, principal.authorities)),
        )
    }

    private companion object {
        val SEQUENCE = AtomicInteger(0)
        const val SCOPE_CODE = "VIN-SCOPE-A"
        const val SCOPE_ADMIN_USERNAME = "vin-scope-admin"
        const val DEFAULT_DEPARTMENT_CODE = "VIN-DEFAULT-DEPT"
    }
}
