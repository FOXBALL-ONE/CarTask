package top.foxball.cartask.service.impl

import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.crypto.password.PasswordEncoder
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

/**
 * 用户名与手机号的唯一性。
 *
 * 数据库上靠 [Table.uniqueConstraints] 兜底，服务层负责给出可读提示。两处都要锁住：
 * 只锁前者，撤销约束不会有测试变红；只锁后者，去掉服务层校验就变成一句 500。
 */
class UserPhoneUniquenessTests {
    private val userRepository = mock<UserRepository>()
    private val dataScopeResolver = mock<DataScopeResolver>()
    private val service = UserServiceImpl(
        userRepository = userRepository,
        departmentRepository = mock<DepartmentRepository>(),
        positionRepository = mock<PositionRepository>(),
        passwordEncoder = mock<PasswordEncoder>(),
        tokenSessionRepository = mock<RedisTokenSessionRepository>(),
        roleAssignmentPolicy = mock<RoleAssignmentPolicy>(),
        dataScopeResolver = dataScopeResolver,
        scopeGuard = ScopeGuard(dataScopeResolver, mock<DepartmentLinkResolver>(), mock<ScopeQuerySupport>()),
        auditService = mock<AuditService>(),
    )

    @Test
    fun `用户表对用户名与手机号声明了具名唯一约束`() {
        val constraints = requireNotNull(User::class.java.getAnnotation(Table::class.java)).uniqueConstraints
            .associate { it.name to it.columnNames.toList() }

        // 名字必须显式给出：Hibernate 自动生成的约束名是哈希串，GlobalExceptionHandler 没法按名字映射，
        // 并发撞车时就只能给调用方一句 500。
        assertEquals(listOf("username"), constraints["uk_users_username"])
        assertEquals(listOf("phone"), constraints["uk_users_phone"])
    }

    @Test
    fun `新建账号时手机号已被占用会被拒绝且不落库`() {
        whenever(userRepository.existsByUsername(any())).thenReturn(false)
        whenever(userRepository.existsByEmail(any())).thenReturn(false)
        whenever(userRepository.existsByPhone("13800138000")).thenReturn(true)

        val error = assertFailsWith<IllegalArgumentException> {
            service.create(
                UserService.CreateCommand(
                    username = "new.user",
                    email = "new.user@local.invalid",
                    credential = "irrelevant",
                    phone = "13800138000",
                ),
            )
        }

        assertTrue(error.message!!.contains("已被其他账号绑定"))
        verify(userRepository, never()).saveAll(any<List<User>>())
    }

    @Test
    fun `更新账号时手机号属于别的账号会被拒绝且不落库`() {
        val target = storedUser(21L)
        whenever(userRepository.findAllById(any<Iterable<Long>>())).thenReturn(listOf(target))
        whenever(userRepository.findById(21L)).thenReturn(Optional.of(target))
        whenever(dataScopeResolver.current()).thenReturn(DataScope.All)
        whenever(userRepository.existsByPhoneAndIdNot("13900139000", 21L)).thenReturn(true)

        val error = assertFailsWith<IllegalArgumentException> {
            service.update(21L, UserService.UpdateCommand(phone = "13900139000"))
        }

        assertTrue(error.message!!.contains("已被其他账号绑定"))
        assertEquals("13800138021", target.phone)
        verify(userRepository, never()).saveAll(any<List<User>>())
    }

    @Test
    fun `手机号去空白后落库且空串存为空值`() {
        val target = storedUser(22L)
        whenever(userRepository.findAllById(any<Iterable<Long>>())).thenReturn(listOf(target))
        whenever(userRepository.findById(22L)).thenReturn(Optional.of(target))
        whenever(userRepository.saveAll(any<List<User>>())).thenAnswer { it.getArgument<List<User>>(0) }
        whenever(dataScopeResolver.current()).thenReturn(DataScope.All)

        service.update(22L, UserService.UpdateCommand(phone = "  13900139001  "))
        // 不去空白的话，「 139… 」与「139…」在唯一约束下算两个值，实际却是同一个登录凭据。
        assertEquals("13900139001", target.phone)

        service.update(22L, UserService.UpdateCommand(phone = ""))
        // 空串一律存 null，否则整个系统只能存在一条空手机号的行。
        assertEquals(null, target.phone)
    }

    private fun storedUser(id: Long): User = User().apply {
        this.id = id
        username = "user$id"
        email = "user$id@local.invalid"
        passwordHash = "encoded"
        role = "USER"
        phone = "138001380$id"
        department = Department().apply {
            this.id = 1L
            name = "运营中心"
            departmentNumber = "OPS"
        }
        createdAt = LocalDateTime.of(2026, 1, 1, 0, 0)
        updatedAt = LocalDateTime.of(2026, 1, 1, 0, 0)
    }
}
