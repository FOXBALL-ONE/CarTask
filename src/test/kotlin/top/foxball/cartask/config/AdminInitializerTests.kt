package top.foxball.cartask.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.event.ApplicationListenerMethodAdapter
import org.springframework.security.crypto.password.PasswordEncoder
import top.foxball.cartask.authentication.SecurityRole
import top.foxball.cartask.entity.Role
import top.foxball.cartask.entity.User
import top.foxball.cartask.repository.PermissionRepository
import top.foxball.cartask.repository.RoleRepository
import top.foxball.cartask.repository.UserRepository

class AdminInitializerTests {
    private val passwordEncoder = mock<PasswordEncoder>()
    private val roleRepository = mock<RoleRepository>()
    private val userRepository = mock<UserRepository>()
    private val permissionRepository = mock<PermissionRepository>()

    @Test
    fun `未启用时不访问数据库`() {
        AdminInitializer(
            AdminInitializerProperties(enabled = false, password = "secret"),
            passwordEncoder,
            roleRepository,
            userRepository,
        ).write()

        verify(roleRepository, never()).findByNameIgnoreCase(any())
        verify(userRepository, never()).findByUsername(any())
    }

    @Test
    fun `启用但密码为空时拒绝启动写入`() {
        val initializer = AdminInitializer(
            AdminInitializerProperties(enabled = true),
            passwordEncoder,
            roleRepository,
            userRepository,
        )

        assertThrows(IllegalArgumentException::class.java) { initializer.write() }
    }

    @Test
    fun `管理员不存在时创建超级管理员`() {
        val role = Role().apply { name = SecurityRole.SUPER_ADMIN }
        whenever(roleRepository.findByNameIgnoreCase(SecurityRole.SUPER_ADMIN)).thenReturn(role)
        whenever(userRepository.findByUsername("admin")).thenReturn(null)
        whenever(passwordEncoder.encode("secret")).thenReturn("hashed")
        val captor = argumentCaptor<User>()

        AdminInitializer(
            AdminInitializerProperties(enabled = true, username = "admin", password = "secret"),
            passwordEncoder,
            roleRepository,
            userRepository,
        ).write()

        verify(userRepository).save(captor.capture())
        assertEquals(SecurityRole.SUPER_ADMIN, captor.firstValue.role)
        assertEquals(setOf(role), captor.firstValue.roles)
        verify(passwordEncoder).encode("secret")
    }

    @Test
    fun `超级管理员角色缺失时一并建出来`() {
        whenever(roleRepository.findByNameIgnoreCase(SecurityRole.SUPER_ADMIN)).thenReturn(null)
        whenever(roleRepository.save(any<Role>())).thenAnswer { it.arguments[0] }
        whenever(userRepository.findByUsername("admin")).thenReturn(null)
        whenever(passwordEncoder.encode("secret")).thenReturn("hashed")
        val captor = argumentCaptor<Role>()

        AdminInitializer(
            AdminInitializerProperties(enabled = true, username = "admin", password = "secret"),
            passwordEncoder,
            roleRepository,
            userRepository,
        ).write()

        verify(roleRepository).save(captor.capture())
        assertEquals(SecurityRole.SUPER_ADMIN, captor.firstValue.name)
        assertTrue(captor.firstValue.enabled)
    }

    @Test
    fun `已有超级管理员且未强制写入时保留原账号`() {
        val role = Role().apply { name = SecurityRole.SUPER_ADMIN }
        val existing = User().apply {
            username = "admin"
            email = "admin@example.com"
            passwordHash = "old-hash"
            this.role = SecurityRole.SUPER_ADMIN
        }
        whenever(roleRepository.findByNameIgnoreCase(SecurityRole.SUPER_ADMIN)).thenReturn(role)
        whenever(userRepository.findByUsername("admin")).thenReturn(existing)

        AdminInitializer(
            AdminInitializerProperties(enabled = true, username = "admin", password = "secret"),
            passwordEncoder,
            roleRepository,
            userRepository,
        ).write()

        assertEquals("old-hash", existing.passwordHash)
        verify(passwordEncoder, never()).encode(any())
        verify(userRepository, never()).save(any())
    }

    
    @Test
    fun `已有的平台管理账号在未强制写入时也提为超级管理员`() {
        val role = Role().apply { name = SecurityRole.SUPER_ADMIN }
        val existing = User().apply {
            username = "admin"
            email = "admin@example.com"
            passwordHash = "old-hash"
            this.role = SecurityRole.ADMIN
        }
        whenever(roleRepository.findByNameIgnoreCase(SecurityRole.SUPER_ADMIN)).thenReturn(role)
        whenever(userRepository.findByUsername("admin")).thenReturn(existing)

        AdminInitializer(
            AdminInitializerProperties(enabled = true, username = "admin", password = "secret"),
            passwordEncoder,
            roleRepository,
            userRepository,
        ).write()

        assertEquals(SecurityRole.SUPER_ADMIN, existing.role)
        assertEquals(setOf(role), existing.roles)
        assertEquals("old-hash", existing.passwordHash)
        verify(passwordEncoder, never()).encode(any())
        verify(userRepository).save(existing)
    }

    @Test
    fun `强制写入时重置已有管理员`() {
        val role = Role().apply { name = SecurityRole.SUPER_ADMIN }
        val existing = User().apply {
            username = "admin"
            email = "admin@example.com"
            passwordHash = "old-hash"
            enabled = false
            status = User.Status.BANNED
        }
        whenever(roleRepository.findByNameIgnoreCase(eq(SecurityRole.SUPER_ADMIN))).thenReturn(role)
        whenever(userRepository.findByUsername("admin")).thenReturn(existing)
        whenever(passwordEncoder.encode("secret")).thenReturn("new-hash")

        AdminInitializer(
            AdminInitializerProperties(enabled = true, username = "admin", password = "secret", forceWrite = true),
            passwordEncoder,
            roleRepository,
            userRepository,
        ).write()

        assertEquals("new-hash", existing.passwordHash)
        assertEquals(SecurityRole.SUPER_ADMIN, existing.role)
        assertEquals(true, existing.enabled)
        assertEquals(User.Status.Activity, existing.status)
        verify(userRepository).save(existing)
    }







    @Test
    fun `运行顺序在角色初始化之后、权限字典之前`() {
        fun orderOf(bean: Any) = ApplicationListenerMethodAdapter(
            bean.javaClass.simpleName,
            bean.javaClass,
            bean.javaClass.getMethod("write"),
        ).order

        val actual = orderOf(
            AdminInitializer(
                AdminInitializerProperties(enabled = false),
                passwordEncoder,
                roleRepository,
                userRepository,
            ),
        )
        val roleInit = orderOf(SystemRoleInitializer(roleRepository))
        val catalogInit = orderOf(PermissionCatalogInitializer(permissionRepository, roleRepository))

        assertTrue(actual > roleInit) { "AdminInitializer($actual) 必须晚于 SystemRoleInitializer($roleInit)" }
        assertTrue(actual < catalogInit) { "AdminInitializer($actual) 必须早于 PermissionCatalogInitializer($catalogInit)" }
    }
}
