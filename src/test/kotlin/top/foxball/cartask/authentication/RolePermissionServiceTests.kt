package top.foxball.cartask.authentication

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.dao.DataAccessResourceFailureException
import top.foxball.cartask.entity.Permission
import top.foxball.cartask.entity.Role
import top.foxball.cartask.entity.UserResourcePermission
import top.foxball.cartask.repository.RoleRepository
import top.foxball.cartask.repository.UserResourcePermissionRepository

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

    @Test
    fun `经办资源授权只增加选定资源及对应审核权限`() {
        val userResourcePermissionRepository = mock<UserResourcePermissionRepository>()
        val role = Role().apply {
            name = "USER"
            permissions = linkedSetOf(
                Permission().apply { code = "dashboard:read" },
                Permission().apply { code = "vehicle-record:read" },
                Permission().apply { code = "person-record:read" },
                Permission().apply { code = "gate-person:review" },
                Permission().apply { code = "vehicle-inout-request:review" },
            )
        }
        whenever(roleRepository.findByNameIgnoreCase("USER")).thenReturn(role)
        whenever(userResourcePermissionRepository.findByUserId(42L)).thenReturn(
            listOf(UserResourcePermission().apply {
                user = top.foxball.cartask.entity.User()
                resourceType = UserResourcePermission.ResourceType.FACE
                reviewEnabled = true
            }),
        )

        val permissions = RolePermissionService(roleRepository, userResourcePermissionRepository).permissionsFor("USER", 42L)

        assertEquals(true, "gate-person:read" in permissions)
        assertEquals(true, "gate-person:review" in permissions)
        assertEquals(true, "person-record:read" in permissions)
        assertEquals(false, "vehicle-record:read" in permissions)
        assertEquals(false, "vehicle-inout-request:review" in permissions)
        assertEquals(true, "dashboard:read" in permissions)
    }

    @Test
    fun `非超级管理员角色配置不能授予车辆或人脸审核`() {
        val role = Role().apply {
            name = "ADMIN"
            permissions = linkedSetOf(
                Permission().apply { code = "vehicle-inout-request:review" },
                Permission().apply { code = "gate-person:review" },
                Permission().apply { code = "vehicle-inout-request:read" },
            )
        }
        whenever(roleRepository.findByNameIgnoreCase("ADMIN")).thenReturn(role)

        assertEquals(setOf("vehicle-inout-request:read"), service.permissionsFor("ADMIN", 10L))
    }
}
