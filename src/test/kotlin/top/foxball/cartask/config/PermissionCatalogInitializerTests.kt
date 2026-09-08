package top.foxball.cartask.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import top.foxball.cartask.authentication.PermissionCatalog
import top.foxball.cartask.entity.Permission
import top.foxball.cartask.repository.PermissionRepository
import top.foxball.cartask.repository.RoleRepository

class PermissionCatalogInitializerTests {
    private val permissionRepository = mock<PermissionRepository>()
    private val roleRepository = mock<RoleRepository>()

    @Test
    fun `只写入缺失的权限编码`() {
        val existing = Permission().apply { code = PermissionCatalog.definitions.first().code; name = "已有名称" }
        val complete = PermissionCatalog.definitions.map { definition -> Permission().apply { code = definition.code; name = definition.name } }
        whenever(permissionRepository.findAll()).thenReturn(listOf(existing), complete)

        PermissionCatalogInitializer(permissionRepository, roleRepository).write()

        val permissions = argumentCaptor<List<Permission>>()
        verify(permissionRepository).saveAll(permissions.capture())
        assertEquals(PermissionCatalog.definitions.size - 1, permissions.firstValue.size)
        verify(roleRepository, never()).save(any())
    }
}
