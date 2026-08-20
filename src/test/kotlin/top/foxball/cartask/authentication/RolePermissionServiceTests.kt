package top.foxball.cartask.authentication

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.dao.DataAccessResourceFailureException
import top.foxball.cartask.entity.Permission
import top.foxball.cartask.entity.Role
import top.foxball.cartask.repository.RoleRepository

class RolePermissionServiceTests {
    private val roleRepository = mock<RoleRepository>()
    private val service = RolePermissionService(roleRepository)

    @Test
    fun `只写入启用且格式正确的权限`() {
        val role = Role().apply {
            name = "ADMIN"
            permissions = linkedSetOf(
                Permission().apply { code = "User:Read" },
                Permission().apply { code = "user:write"; enabled = false },
            )
        }
        whenever(roleRepository.findByNameIgnoreCase("ADMIN")).thenReturn(role)

        assertEquals(setOf("user:read"), service.permissionsFor("admin"))
    }

    @Test
    fun `启用权限编码非法时认证基础设施异常`() {
        val role = Role().apply {
            name = "ADMIN"
            permissions = linkedSetOf(Permission().apply { code = "invalid permission" })
        }
        whenever(roleRepository.findByNameIgnoreCase("ADMIN")).thenReturn(role)

        assertThrows(AuthenticationInfrastructureException::class.java) {
            service.permissionsFor("ADMIN")
        }
    }

    @Test
    fun `规范化后权限编码重复时认证基础设施异常`() {
        val role = Role().apply {
            name = "ADMIN"
            permissions = linkedSetOf(
                Permission().apply { code = "User:Read" },
                Permission().apply { code = "user:read" },
            )
        }
        whenever(roleRepository.findByNameIgnoreCase("ADMIN")).thenReturn(role)

        assertThrows(AuthenticationInfrastructureException::class.java) {
            service.permissionsFor("ADMIN")
        }
    }

    @Test
    fun `未配置角色时拒绝认证`() {
        whenever(roleRepository.findByNameIgnoreCase("ADMIN")).thenReturn(null)

        assertThrows(JwtAuthenticationException::class.java) {
            service.permissionsFor("ADMIN")
        }
    }

    @Test
    fun `读取权限失败时标记为认证基础设施异常`() {
        whenever(roleRepository.findByNameIgnoreCase("ADMIN"))
            .thenThrow(DataAccessResourceFailureException("database unavailable"))

        assertThrows(AuthenticationInfrastructureException::class.java) {
            service.permissionsFor("ADMIN")
        }
    }

    @Test
    fun `已配置但禁用的角色必须拒绝认证`() {
        val role = Role().apply {
            name = "ADMIN"
            enabled = false
        }
        whenever(roleRepository.findByNameIgnoreCase("ADMIN")).thenReturn(role)

        assertThrows(JwtAuthenticationException::class.java) {
            service.permissionsFor("ADMIN")
        }
    }
}
