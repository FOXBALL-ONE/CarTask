package top.foxball.cartask.scope

import java.util.Optional
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import top.foxball.cartask.authentication.CurrentUserPrincipal
import top.foxball.cartask.entity.Department
import top.foxball.cartask.entity.GatePerson
import top.foxball.cartask.entity.ParkingPlate
import top.foxball.cartask.entity.User
import top.foxball.cartask.repository.GatePersonRepository
import top.foxball.cartask.repository.ParkingPlateRepository
import top.foxball.cartask.repository.UserManagedDepartmentRepository
import top.foxball.cartask.repository.UserRepository
import java.time.LocalDateTime

class DataScopeResolverTests {
    private val departmentLinkResolver = mock<DepartmentLinkResolver>()
    private val scopeQuerySupport = mock<ScopeQuerySupport>()
    private val userRepository = mock<UserRepository>()
    private val userManagedDepartmentRepository = mock<UserManagedDepartmentRepository>()
    private val parkingPlateRepository = mock<ParkingPlateRepository>()
    private val gatePersonRepository = mock<GatePersonRepository>()
    private val resolver = DataScopeResolver(
        departmentLinkResolver,
        scopeQuerySupport,
        userRepository,
        userManagedDepartmentRepository,
        parkingPlateRepository,
        gatePersonRepository,
    )

    private val root = department(1L, "运营中心", "OPS")
    private val parking = department(2L, "停车管理组", "PARKING", root)
    private val security = department(3L, "门禁安全组", "SECURITY", root)

    private fun department(id: Long, name: String, code: String, superior: Department? = null) =
        Department().apply {
            this.id = id
            this.name = name
            departmentNumber = code
            this.superior = superior
        }

    private fun user(id: Long, phone: String?, department: Department?) = User().apply {
        this.id = id
        username = "u$id"
        email = "u$id@local.invalid"
        passwordHash = "x"
        this.phone = phone
        this.department = department
        createdAt = LocalDateTime.of(2026, 1, 1, 0, 0)
        updatedAt = createdAt
    }

    private fun principal(role: String, userId: Long = 7L, workingDepartmentId: Long? = null) =
        CurrentUserPrincipal(userId, "u$userId", role, "jti-1", workingDepartmentId = workingDepartmentId)

    @Test
    fun `超级管理员默认不限部门`() {
        whenever(departmentLinkResolver.snapshot()).thenReturn(DepartmentSnapshot(listOf(root, parking, security)))

        val scope = resolver.forPrincipal(principal("SUPER_ADMIN"))

        assertEquals(ScopeKind.ALL, scope.kind)
        assertTrue(scope.unrestricted)
    }

    @Test
    fun `平台管理默认不限部门`() {
        whenever(departmentLinkResolver.snapshot()).thenReturn(DepartmentSnapshot(listOf(root, parking, security)))

        assertEquals(ScopeKind.ALL, resolver.forPrincipal(principal("ADMIN")).kind)
    }

    @Test
    fun `超级管理员选定工作部门后收窄到该部门及其下级`() {
        whenever(departmentLinkResolver.snapshot()).thenReturn(DepartmentSnapshot(listOf(root, parking, security)))

        val scope = resolver.forPrincipal(principal("SUPER_ADMIN", workingDepartmentId = root.id))

        assertEquals(ScopeKind.DEPARTMENTS, scope.kind)
        assertEquals(setOf(1L, 2L, 3L), scope.departmentIds)
        assertEquals(setOf("OPS", "PARKING", "SECURITY"), scope.departmentCodes)
    }

    @Test
    fun `部门管理限定在被分配的部门`() {
        whenever(departmentLinkResolver.snapshot()).thenReturn(DepartmentSnapshot(listOf(root, parking, security)))
        whenever(userManagedDepartmentRepository.findDepartmentIds(7L)).thenReturn(listOf(parking.id!!))
        whenever(userManagedDepartmentRepository.findDepartmentIdsWithDescendants(7L)).thenReturn(emptyList())

        val scope = resolver.forPrincipal(principal("DEPT_ADMIN"))

        assertEquals(ScopeKind.DEPARTMENTS, scope.kind)
        assertEquals(setOf(parking.id), scope.departmentIds)
    }

    @Test
    fun `部门管理选定工作部门时收窄到该部门`() {
        whenever(departmentLinkResolver.snapshot()).thenReturn(DepartmentSnapshot(listOf(root, parking, security)))
        // 分配了「运营中心及以下」，工作部门选了下级「停车管理组」。
        whenever(userManagedDepartmentRepository.findDepartmentIds(7L)).thenReturn(listOf(root.id!!))
        whenever(userManagedDepartmentRepository.findDepartmentIdsWithDescendants(7L)).thenReturn(listOf(root.id!!))

        val scope = resolver.forPrincipal(principal("DEPT_ADMIN", workingDepartmentId = parking.id))

        assertEquals(setOf(parking.id), scope.departmentIds)
    }

    @Test
    fun `部门管理的工作部门不在分配范围内时用整个分配范围`() {
        whenever(departmentLinkResolver.snapshot()).thenReturn(DepartmentSnapshot(listOf(root, parking, security)))
        whenever(userManagedDepartmentRepository.findDepartmentIds(7L)).thenReturn(listOf(parking.id!!))
        whenever(userManagedDepartmentRepository.findDepartmentIdsWithDescendants(7L)).thenReturn(emptyList())

        val scope = resolver.forPrincipal(principal("DEPT_ADMIN", workingDepartmentId = security.id))

        assertEquals(setOf(parking.id), scope.departmentIds)
    }

    @Test
    fun `部门管理未标记含下级时只覆盖本部门`() {
        whenever(departmentLinkResolver.snapshot()).thenReturn(DepartmentSnapshot(listOf(root, parking, security)))
        whenever(userManagedDepartmentRepository.findDepartmentIds(7L)).thenReturn(listOf(root.id!!))
        whenever(userManagedDepartmentRepository.findDepartmentIdsWithDescendants(7L)).thenReturn(emptyList())

        val scope = resolver.forPrincipal(principal("DEPT_ADMIN"))

        // 没勾「含下级」就不应把下级部门一并纳入，否则这个标记就没有意义。
        assertEquals(setOf(root.id), scope.departmentIds)
    }

    @Test
    fun `部门管理带含下级标记时展开下级部门`() {
        whenever(departmentLinkResolver.snapshot()).thenReturn(DepartmentSnapshot(listOf(root, parking, security)))
        whenever(userManagedDepartmentRepository.findDepartmentIds(7L)).thenReturn(listOf(root.id!!))
        whenever(userManagedDepartmentRepository.findDepartmentIdsWithDescendants(7L)).thenReturn(listOf(root.id!!))

        val scope = resolver.forPrincipal(principal("DEPT_ADMIN"))

        assertEquals(setOf(1L, 2L, 3L), scope.departmentIds)
    }

    @Test
    fun `部门管理没有分配范围时退回归属部门`() {
        whenever(departmentLinkResolver.snapshot()).thenReturn(DepartmentSnapshot(listOf(root, parking, security)))
        whenever(userManagedDepartmentRepository.findDepartmentIds(7L)).thenReturn(emptyList())
        whenever(userManagedDepartmentRepository.findDepartmentIdsWithDescendants(7L)).thenReturn(emptyList())
        whenever(userRepository.findById(7L)).thenReturn(Optional.of(user(7L, "13800138000", parking)))

        val scope = resolver.forPrincipal(principal("DEPT_ADMIN"))

        assertEquals(setOf(parking.id), scope.departmentIds)
    }

    @Test
    fun `普通用户为本人范围并带着自己的车牌与门禁身份`() {
        whenever(userRepository.findById(7L)).thenReturn(Optional.of(user(7L, "13800138000", null)))
        whenever(scopeQuerySupport.ownersOf(7L, "13800138000")).thenReturn(emptyList())
        whenever(parkingPlateRepository.findByLinkedUserId(7L)).thenReturn(
            listOf(ParkingPlate().apply { plate = "粤A·12345"; ownerId = 1; owner = "张三"; status = 1; regDate = java.time.LocalDate.of(2026, 1, 1); createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now() }),
        )
        whenever(gatePersonRepository.findByPhone("13800138000")).thenReturn(
            listOf(GatePerson().apply {
                code = "GP-1"; dept = "停车管理组"; name = "张三"; phone = "13800138000"; idCard = "X"
                createTime = LocalDateTime.now(); updatedAt = createTime
            }),
        )

        val scope = resolver.forPrincipal(principal("USER"))

        assertEquals(ScopeKind.SELF, scope.kind)
        // 车牌按统一规则归一化，范围里的车牌与进出记录的归一化车牌才能对上。
        assertEquals(setOf("粤A12345"), scope.carNumbers)
        assertEquals(setOf("GP-1"), scope.gatePersonCodes)
        assertEquals(setOf("张三"), scope.gatePersonNames)
        assertFalse(scope.deniesEverything)
    }

    @Test
    fun `普通用户没有关联到任何数据时可见范围为空`() {
        whenever(userRepository.findById(7L)).thenReturn(Optional.of(user(7L, null, null)))
        whenever(scopeQuerySupport.ownersOf(7L, null)).thenReturn(emptyList())
        whenever(parkingPlateRepository.findByLinkedUserId(7L)).thenReturn(emptyList())

        val scope = resolver.forPrincipal(principal("USER"))

        assertTrue(scope.deniesEverything)
    }
}
