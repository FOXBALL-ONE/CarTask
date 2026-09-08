package top.foxball.cartask.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.crypto.password.PasswordEncoder
import top.foxball.cartask.entity.Role
import top.foxball.cartask.entity.User
import top.foxball.cartask.repository.RoleRepository
import top.foxball.cartask.repository.UserRepository

class AdminInitializerTests {
    private val passwordEncoder = mock<PasswordEncoder>()
    private val roleRepository = mock<RoleRepository>()
    private val userRepository = mock<UserRepository>()

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
    fun `管理员不存在时创建管理员`() {
        val role = Role().apply { name = "ADMIN" }
        whenever(roleRepository.findByNameIgnoreCase("ADMIN")).thenReturn(role)
        whenever(userRepository.findByUsername("admin")).thenReturn(null)
        whenever(passwordEncoder.encode("secret")).thenReturn("hashed")

        AdminInitializer(
            AdminInitializerProperties(enabled = true, username = "admin", password = "secret"),
            passwordEncoder,
            roleRepository,
            userRepository,
        ).write()

        verify(userRepository).save(any())
        verify(passwordEncoder).encode("secret")
    }

    @Test
    fun `已有管理员且未强制写入时保留原账号`() {
        val role = Role().apply { name = "ADMIN" }
        val existing = User().apply {
            username = "admin"
            email = "admin@example.com"
            passwordHash = "old-hash"
        }
        whenever(roleRepository.findByNameIgnoreCase("ADMIN")).thenReturn(role)
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
    fun `强制写入时重置已有管理员`() {
        val role = Role().apply { name = "ADMIN" }
        val existing = User().apply {
            username = "admin"
            email = "admin@example.com"
            passwordHash = "old-hash"
            enabled = false
            status = User.Status.BANNED
        }
        whenever(roleRepository.findByNameIgnoreCase(eq("ADMIN"))).thenReturn(role)
        whenever(userRepository.findByUsername("admin")).thenReturn(existing)
        whenever(passwordEncoder.encode("secret")).thenReturn("new-hash")

        AdminInitializer(
            AdminInitializerProperties(enabled = true, username = "admin", password = "secret", forceWrite = true),
            passwordEncoder,
            roleRepository,
            userRepository,
        ).write()

        assertEquals("new-hash", existing.passwordHash)
        assertEquals("ADMIN", existing.role)
        assertEquals(true, existing.enabled)
        assertEquals(User.Status.Activity, existing.status)
        verify(userRepository).save(existing)
    }
}
