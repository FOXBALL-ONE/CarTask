package top.foxball.cartask.service.impl

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import top.foxball.cartask.entity.Permission
import top.foxball.cartask.entity.Role
import top.foxball.cartask.authentication.RedisTokenSessionRepository
import top.foxball.cartask.repository.PermissionRepository
import top.foxball.cartask.repository.RoleRepository
import top.foxball.cartask.repository.UserRepository

class RolePermissionCodeNormalizationTests {
    private val roleRepository = mock<RoleRepository>()
    private val permissionRepository = mock<PermissionRepository>()
    private val userRepository = mock<UserRepository>()
    private val tokenSessionRepository = mock<RedisTokenSessionRepository>()
    private val roleService = RoleServiceImpl(roleRepository, userRepository, tokenSessionRepository)
    private val permissionService = PermissionServiceImpl(
        permissionRepository,
        roleRepository,
        userRepository,
        tokenSessionRepository,
    )

    @Test
    fun `创建角色时规范化角色编码`() {
        val role = Role().apply { name = " role_admin " }
        whenever(roleRepository.save(role)).thenReturn(role)

        roleService.create(role)

        assertEquals("ADMIN", role.name)
    }

    @Test
    fun `创建权限时规范化权限编码`() {
        val permission = Permission().apply {
            code = " User:Read "
            name = "查看用户"
        }
        whenever(permissionRepository.save(permission)).thenReturn(permission)

        permissionService.create(permission)

        assertEquals("user:read", permission.code)
    }

    @Test
    fun `超级管理员角色必须保留治理权限`() {
        val role = Role().apply { name = "SUPER_ADMIN" }

        assertThrows(IllegalArgumentException::class.java) { roleService.create(role) }
    }

    @Test
    fun `系统角色不能删除`() {
        val role = Role().apply { id = 7; name = "ADMIN" }
        whenever(roleRepository.findById(7)).thenReturn(java.util.Optional.of(role))

        assertThrows(org.springframework.security.access.AccessDeniedException::class.java) { roleService.delete(7) }
    }
}
