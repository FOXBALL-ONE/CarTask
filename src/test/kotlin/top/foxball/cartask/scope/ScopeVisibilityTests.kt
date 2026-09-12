package top.foxball.cartask.scope

import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import top.foxball.cartask.entity.Department
import top.foxball.cartask.entity.ParkingOwner
import top.foxball.cartask.entity.ParkingPlate
import top.foxball.cartask.entity.StoredFile
import top.foxball.cartask.entity.ViolationSubject
import top.foxball.cartask.repository.DepartmentRepository
import top.foxball.cartask.repository.GatePersonRepository
import top.foxball.cartask.repository.ParkingOwnerRepository
import top.foxball.cartask.repository.ParkingPlateRepository

/** 文件与违规主体的可见性判定：这两处原先完全没有归属字段，是本次新补的范围入口。 */
class ScopeVisibilityTests {
    private val departmentLinkResolver = mock<DepartmentLinkResolver>()
    private val parkingOwnerRepository = mock<ParkingOwnerRepository>()
    private val parkingPlateRepository = mock<ParkingPlateRepository>()
    private val gatePersonRepository = mock<GatePersonRepository>()
    private val support = ScopeQuerySupport(
        departmentLinkResolver,
        parkingOwnerRepository,
        parkingPlateRepository,
        gatePersonRepository,
    )

    private val departmentScope = DataScope.departments(setOf(1L), setOf("PARKING"), setOf("停车管理组"))
    private val selfScope = DataScope.self(
        userId = 7L,
        phone = "13800138000",
        carNumbers = setOf("粤A12345"),
        gatePersonCodes = setOf("GP-1"),
        gatePersonNames = setOf("张三"),
    )

    private fun file(
        departmentCode: String? = null,
        uploadedByUserId: Long? = null,
        businessType: String? = null,
        businessId: String? = null,
    ) = StoredFile().apply {
        id = UUID.randomUUID()
        originalFilename = "capture.jpg"
        storedFilename = "capture.jpg"
        relativePath = "2026/01/01/capture.jpg"
        sha256 = "x"
        createdAt = LocalDateTime.of(2026, 1, 1, 0, 0)
        this.departmentCode = departmentCode
        this.uploadedByUserId = uploadedByUserId
        this.businessType = businessType
        this.businessId = businessId
    }

    private fun subject(
        type: ViolationSubject.SubjectType,
        number: String,
        departmentCode: String? = null,
        linkedUserId: Long? = null,
    ) = ViolationSubject().apply {
        subjectType = type
        subjectName = "张三"
        subjectNumber = number
        this.departmentCode = departmentCode
        this.linkedUserId = linkedUserId
        createdAt = LocalDateTime.of(2026, 1, 1, 0, 0)
        updatedAt = createdAt
    }

    @Test
    fun `不限范围时文件一律可见`() {
        assertTrue(support.fileVisible(DataScope.All, file()))
    }

    @Test
    fun `部门范围按归属部门判定文件可见性且缺归属时不可见`() {
        assertTrue(support.fileVisible(departmentScope, file(departmentCode = "PARKING")))
        assertTrue(support.fileVisible(departmentScope, file(departmentCode = "OTHER")).not())
        // 没有任何归属的文件对受限角色不可见：fail closed，否则等于换个 UUID 就能下载。
        assertFalse(support.fileVisible(departmentScope, file()))
    }

    @Test
    fun `部门范围可按抓拍图片的车牌反查归属`() {
        whenever(departmentLinkResolver.snapshot()).thenReturn(
            DepartmentSnapshot(
                listOf(
                    Department().apply {
                        id = 1L
                        name = "停车管理组"
                        departmentNumber = "PARKING"
                        sortOrder = 0
                        status = 1
                    },
                ),
            ),
        )
        whenever(parkingOwnerRepository.findAll()).thenReturn(
            listOf(owner(1L, "张三", "13800138000", "PARKING")),
        )
        whenever(parkingPlateRepository.findByOwnerIdIn(setOf(1L))).thenReturn(
            listOf(plate("粤A·12345", 1L)),
        )

        // 同步任务写入的抓拍图片没有上传者，归属只能靠车牌反查——这条路径替代了写死的部门快照。
        val photo = file(businessType = StoredFile.BUSINESS_VEHICLE_PLATE, businessId = "粤A12345")
        assertTrue(support.fileVisible(departmentScope, photo))
    }

    @Test
    fun `本人范围可按上传者或业务对象判定文件可见性`() {
        assertTrue(support.fileVisible(selfScope, file(uploadedByUserId = 7L)))
        assertTrue(
            support.fileVisible(
                selfScope,
                file(businessType = StoredFile.BUSINESS_VEHICLE_PLATE, businessId = "粤A12345"),
            ),
        )
        assertTrue(
            support.fileVisible(
                selfScope,
                file(businessType = StoredFile.BUSINESS_GATE_PERSON, businessId = "GP-1"),
            ),
        )
        // 别人的图片：既不是本人上传，车牌与门禁编号也不属于本人。
        assertFalse(
            support.fileVisible(
                selfScope,
                file(uploadedByUserId = 9L, businessType = StoredFile.BUSINESS_VEHICLE_PLATE, businessId = "粤A99999"),
            ),
        )
    }

    @Test
    fun `违规主体按部门归属判定可见性`() {
        assertTrue(
            support.violationSubjectVisible(departmentScope, subject(ViolationSubject.SubjectType.VEHICLE, "粤A12345", "PARKING")),
        )
        assertFalse(
            support.violationSubjectVisible(departmentScope, subject(ViolationSubject.SubjectType.VEHICLE, "粤A99999", "OTHER")),
        )
        assertFalse(
            support.violationSubjectVisible(departmentScope, subject(ViolationSubject.SubjectType.VEHICLE, "粤A99999")),
        )
    }

    @Test
    fun `违规主体按关联账号或车牌工号判定本人可见性`() {
        assertTrue(
            support.violationSubjectVisible(selfScope, subject(ViolationSubject.SubjectType.VEHICLE, "无所谓", linkedUserId = 7L)),
        )
        assertTrue(
            support.violationSubjectVisible(selfScope, subject(ViolationSubject.SubjectType.VEHICLE, "粤A·12345")),
        )
        assertTrue(
            support.violationSubjectVisible(selfScope, subject(ViolationSubject.SubjectType.PERSON, "GP-1")),
        )
        assertFalse(
            support.violationSubjectVisible(selfScope, subject(ViolationSubject.SubjectType.VEHICLE, "粤A99999")),
        )
    }

    private fun owner(id: Long, name: String, phone: String, departmentCode: String?) = ParkingOwner().apply {
        this.id = id
        cardId = "CARD-$id"
        this.name = name
        dept = "停车管理组"
        this.phone = phone
        this.departmentCode = departmentCode
        spotCount = 0
        plateCount = 1
        createdAt = LocalDateTime.of(2026, 1, 1, 0, 0)
        updatedAt = createdAt
    }

    private fun plate(number: String, ownerId: Long) = ParkingPlate().apply {
        plate = number
        owner = "张三"
        this.ownerId = ownerId
        status = 1
        regDate = java.time.LocalDate.of(2026, 1, 1)
        createdAt = LocalDateTime.of(2026, 1, 1, 0, 0)
        updatedAt = createdAt
    }
}

/** Excel 导入导出的范围策略。 */
class ExcelResourcePolicyTests {
    private val scopeGuard = mock<ScopeGuard>()
    private val departmentRepository = mock<DepartmentRepository>()
    private val policy = ExcelResourcePolicy(scopeGuard, departmentRepository)

    private fun department(id: Long, code: String, name: String) = Department().apply {
        this.id = id
        departmentNumber = code
        this.name = name
        sortOrder = 0
        status = 1
    }

    @Test
    fun `不限范围时任何资源都可处理`() {
        whenever(scopeGuard.currentScope()).thenReturn(DataScope.All)

        policy.requireScopable("all")
        policy.requireScopable("devices")
        assertNull(policy.forcedImportDepartment())
    }

    @Test
    fun `受限范围拒绝没有部门归属的资源`() {
        whenever(scopeGuard.currentScope()).thenReturn(DataScope.departments(setOf(1L), setOf("PARKING"), setOf("停车管理组")))

        // 这些资源在表结构上没有部门字段，"按范围裁剪"是做不到的，只能拒绝而不是给一份全量数据。
        assertThrows(IllegalArgumentException::class.java) { policy.requireScopable("all") }
        assertThrows(IllegalArgumentException::class.java) { policy.requireScopable("devices") }
        assertThrows(IllegalArgumentException::class.java) { policy.requireScopable("positions") }
        assertThrows(IllegalArgumentException::class.java) { policy.requireScopable("departments") }
        // 可裁剪的资源正常放行。
        policy.requireScopable("owners")
        policy.requireScopable("plates")
    }

    @Test
    fun `受限范围导入时强制落到范围内的唯一部门`() {
        whenever(scopeGuard.currentScope())
            .thenReturn(DataScope.departments(setOf(1L), setOf("PARKING"), setOf("停车管理组")))
        whenever(departmentRepository.findById(1L)).thenReturn(Optional.of(department(1L, "PARKING", "停车管理组")))

        val forced = policy.forcedImportDepartment()

        assertEquals("PARKING", forced?.code)
        assertEquals("停车管理组", forced?.name)
    }

    @Test
    fun `受限但未确定唯一部门时拒绝导入`() {
        whenever(scopeGuard.currentScope())
            .thenReturn(DataScope.departments(setOf(1L, 2L), setOf("A", "B"), setOf("甲", "乙")))

        // 导入总得落到一个部门，范围里有两个又没选工作部门时不能猜。
        assertThrows(IllegalStateException::class.java) { policy.forcedImportDepartment() }
    }
}
