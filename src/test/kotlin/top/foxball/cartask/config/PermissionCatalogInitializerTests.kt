package top.foxball.cartask.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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

    @Test
    fun `为已有管理员角色补齐系统监控权限`() {
        val dashboard = Permission().apply { code = "dashboard:read"; name = "查看仪表盘" }
        val monitor = Permission().apply { code = PermissionCatalog.SYSTEM_MONITOR_READ; name = "查看系统监控" }
        val admin = top.foxball.cartask.entity.Role().apply {
            name = "ADMIN"
            permissions = linkedSetOf(dashboard)
        }
        val allPermissions = listOf(dashboard, monitor)
        whenever(permissionRepository.findAll()).thenReturn(allPermissions, allPermissions)
        whenever(roleRepository.findByNameIgnoreCase("ADMIN")).thenReturn(admin)

        PermissionCatalogInitializer(permissionRepository, roleRepository).write()

        assertTrue(admin.permissions.any { it.code == PermissionCatalog.SYSTEM_MONITOR_READ })
        verify(roleRepository).save(admin)
    }

    @Test
    fun `启动时修正旧版系统监控权限编码`() {
        val legacyMonitor = Permission().apply {
            code = PermissionCatalog.LEGACY_SYSTEM_MONITOR_READ
            name = "查看系统监控"
        }
        whenever(permissionRepository.findAll()).thenReturn(listOf(legacyMonitor), listOf(legacyMonitor), listOf(legacyMonitor))
        whenever(permissionRepository.save(legacyMonitor)).thenReturn(legacyMonitor)
        whenever(roleRepository.findAll()).thenReturn(emptyList())

        PermissionCatalogInitializer(permissionRepository, roleRepository).write()

        assertEquals(PermissionCatalog.SYSTEM_MONITOR_READ, legacyMonitor.code)
        verify(permissionRepository).save(legacyMonitor)
    }
}
