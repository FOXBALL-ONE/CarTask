package top.foxball.cartask.service.impl

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.crypto.password.PasswordEncoder
import top.foxball.cartask.audit.AuditCommand
import top.foxball.cartask.audit.AuditService
import top.foxball.cartask.authentication.RedisTokenSessionRepository
import top.foxball.cartask.authentication.RoleAssignmentPolicy
import top.foxball.cartask.entity.Department
import top.foxball.cartask.entity.User
import top.foxball.cartask.repository.DepartmentRepository
import top.foxball.cartask.repository.PositionRepository
import top.foxball.cartask.repository.UserRepository
import top.foxball.cartask.scope.DataScope
import top.foxball.cartask.scope.DataScopeResolver
import top.foxball.cartask.scope.DepartmentLinkResolver
import top.foxball.cartask.scope.ScopeGuard
import top.foxball.cartask.scope.ScopeQuerySupport
import top.foxball.cartask.service.UserService
import java.time.LocalDateTime
import java.util.Optional







class UserServiceScopeTests {
    private val userRepository = mock<UserRepository>()
    private val dataScopeResolver = mock<DataScopeResolver>()
    private val auditService = mock<AuditService>()
    private val scopeGuard = ScopeGuard(dataScopeResolver, mock<DepartmentLinkResolver>(), mock<ScopeQuerySupport>())
    private val service = UserServiceImpl(
        userRepository = userRepository,
        departmentRepository = mock<DepartmentRepository>(),
        positionRepository = mock<PositionRepository>(),
        passwordEncoder = mock<PasswordEncoder>(),
        tokenSessionRepository = mock<RedisTokenSessionRepository>(),
        roleAssignmentPolicy = mock<RoleAssignmentPolicy>(),
        dataScopeResolver = dataScopeResolver,
        scopeGuard = scopeGuard,
        auditService = auditService,
    )

    private val departmentScope = DataScope.departments(
        ids = setOf(1L, 2L),
        codes = setOf("OPS", "PARKING"),
        names = setOf("运营中心", "停车管理组"),
    )

    private fun storedUser(id: Long, departmentId: Long?): User = User().apply {
        this.id = id
        username = "user$id"
        email = "user$id@local.invalid"
        passwordHash = "encoded"
        role = "USER"
        phone = "138001380${id.toString().padStart(2, '0')}"
        department = departmentId?.let { departmentId ->
            Department().apply {
                this.id = departmentId
                name = "部门$departmentId"
                departmentNumber = "D$departmentId"
            }
        }
        createdAt = LocalDateTime.of(2026, 1, 1, 0, 0)
        updatedAt = LocalDateTime.of(2026, 1, 1, 0, 0)
    }

    private fun stubUsers(vararg users: User) {
        whenever(userRepository.findAllById(any<Iterable<Long>>())).thenReturn(users.toList())
        users.forEach { whenever(userRepository.findById(it.id!!)).thenReturn(Optional.of(it)) }
        whenever(userRepository.saveAll(any<List<User>>())).thenAnswer { it.getArgument<List<User>>(0) }
    }

    @Test
    fun `部门管理改本部门账号的手机号会被放行`() {
        val inScope = storedUser(11L, 2L)
        stubUsers(inScope)
        whenever(dataScopeResolver.current()).thenReturn(departmentScope)

        service.update(11L, UserService.UpdateCommand(phone = "13900139000"))

        assertEquals("13900139000", inScope.phone)
    }

    @Test
    fun `部门管理改范围外账号的手机号会被拒绝且不落库`() {
        val other = storedUser(12L, 99L)
        stubUsers(other)
        whenever(dataScopeResolver.current()).thenReturn(departmentScope)

        assertThrows(AccessDeniedException::class.java) {
            service.update(12L, UserService.UpdateCommand(phone = "13900139001"))
        }

        assertEquals("13800138012", other.phone)
        verify(userRepository, never()).saveAll(any<List<User>>())
    }

    @Test
    fun `受限范围下没有部门归属的账号同样拒绝`() {
        val orphan = storedUser(13L, null)
        stubUsers(orphan)
        whenever(dataScopeResolver.current()).thenReturn(departmentScope)

        assertThrows(AccessDeniedException::class.java) {
            service.update(13L, UserService.UpdateCommand(phone = "13900139002"))
        }

        verify(userRepository, never()).saveAll(any<List<User>>())
    }

    @Test
    fun `不限范围时可以改任意账号的手机号`() {
        val other = storedUser(12L, 99L)
        stubUsers(other)
        whenever(dataScopeResolver.current()).thenReturn(DataScope.All)
        val captor = argumentCaptor<AuditCommand>()

        service.update(12L, UserService.UpdateCommand(phone = "13900139001"))

        assertEquals("13900139001", other.phone)
        verify(auditService).record(captor.capture())
        assertEquals("13800138012", captor.firstValue.beforeData?.get("phone"))
        assertEquals("13900139001", captor.firstValue.afterData?.get("phone"))
    }
}
