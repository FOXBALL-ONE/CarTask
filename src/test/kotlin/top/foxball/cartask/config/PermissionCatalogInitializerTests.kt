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
    fun `部门管理角色获得业务权限但不含治理与无部门关联的只读权限`() {
        val complete = PermissionCatalog.definitions.map { definition ->
            Permission().apply { code = definition.code; name = definition.name }
        }
        whenever(permissionRepository.findAll()).thenReturn(complete)
        val deptAdmin = top.foxball.cartask.entity.Role().apply { name = "DEPT_ADMIN" }
        whenever(roleRepository.findByNameIgnoreCase("DEPT_ADMIN")).thenReturn(deptAdmin)

        PermissionCatalogInitializer(permissionRepository, roleRepository).write()

        val granted = deptAdmin.permissions.map { it.code }.toSet()
        assertTrue(
            granted.containsAll(
                listOf(
                    "vehicle-record:read",
                    "person-record:read",
                    "owner:manage",
                    "plate:manage",
                    "gate-person:read",
                    "user:create",
                ),
            ),
        )
        val denied = setOf(
            "role:manage",
            "permission:manage",
            "user:role-assign",
            "user:disable",
            "department:manage",
            "audit:read",
            "audit:delete",
            "system-monitor:read",
            "device:read",
            "device:manage",
            "position:read",
            "dictionary:manage",
            "backup:manage",
        )
        assertTrue(
            granted.intersect(denied).isEmpty(),
            "部门管理不应获得这些权限：${granted.intersect(denied)}",
        )
    }

    @Test
    fun `平台管理角色拿不到数据备份权限`() {
        val complete = PermissionCatalog.definitions.map { definition ->
            Permission().apply { code = definition.code; name = definition.name }
        }
        whenever(permissionRepository.findAll()).thenReturn(complete, complete)
        val admin = top.foxball.cartask.entity.Role().apply { name = "ADMIN" }
        whenever(roleRepository.findByNameIgnoreCase("ADMIN")).thenReturn(admin)

        PermissionCatalogInitializer(permissionRepository, roleRepository).write()

        // 备份产物是整库 SQL 加全部附件（含生物特征照片与口令散列），只留给超级管理员。
        assertTrue(
            admin.permissions.none { it.code == "backup:manage" },
            "平台管理不应获得数据备份权限，实际：${admin.permissions.map { it.code }}",
        )
    }

    @Test
    fun `普通用户获得自助读取权限但不含任何管理权限`() {
        val complete = PermissionCatalog.definitions.map { definition ->
            Permission().apply { code = definition.code; name = definition.name }
        }
        whenever(permissionRepository.findAll()).thenReturn(complete)
        val user = top.foxball.cartask.entity.Role().apply { name = "USER" }
        whenever(roleRepository.findByNameIgnoreCase("USER")).thenReturn(user)

        PermissionCatalogInitializer(permissionRepository, roleRepository).write()

        val granted = user.permissions.map { it.code }.toSet()
        // 普通用户要能看到自己的进出记录；这些接口本身按本人范围过滤，所以给读权限不越权。
        assertTrue(granted.containsAll(listOf("dashboard:read", "vehicle-record:read", "person-record:read")))
        assertTrue(
            granted.none { it.endsWith(":manage") || it.endsWith(":create") || it.endsWith(":update") || it.endsWith(":disable") },
            "普通用户不应获得任何管理类权限，实际：$granted",
        )
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
