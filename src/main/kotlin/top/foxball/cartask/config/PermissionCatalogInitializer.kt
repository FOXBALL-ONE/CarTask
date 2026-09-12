package top.foxball.cartask.config

import jakarta.transaction.Transactional
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import top.foxball.cartask.authentication.PermissionCatalog
import top.foxball.cartask.authentication.SecurityRole
import top.foxball.cartask.entity.Permission
import top.foxball.cartask.repository.PermissionRepository
import top.foxball.cartask.repository.RoleRepository

/** 补齐内置权限字典，不覆盖数据库中已有权限的名称或启用状态。 */
@Component
class PermissionCatalogInitializer(
    private val permissionRepository: PermissionRepository,
    private val roleRepository: RoleRepository,
) {
    @Order(Ordered.LOWEST_PRECEDENCE)
    @EventListener(classes = [ApplicationReadyEvent::class])
    @Transactional
    fun write() {
        val existingPermissions = permissionRepository.findAll().toMutableList()
        val legacyMonitorPermission = existingPermissions.singleOrNull { permission ->
            permission.code.equals(PermissionCatalog.LEGACY_SYSTEM_MONITOR_READ, ignoreCase = true)
        }
        if (legacyMonitorPermission != null) {
            val monitorPermission = existingPermissions.singleOrNull { permission ->
                permission.code == PermissionCatalog.SYSTEM_MONITOR_READ
            }
            if (monitorPermission == null) {
                legacyMonitorPermission.code = PermissionCatalog.SYSTEM_MONITOR_READ
                permissionRepository.save(legacyMonitorPermission)
            } else {
                roleRepository.findAll().forEach { role ->
                    if (role.permissions.remove(legacyMonitorPermission)) {
                        role.permissions.add(monitorPermission)
                        roleRepository.save(role)
                    }
                }
                permissionRepository.delete(legacyMonitorPermission)
                existingPermissions.remove(legacyMonitorPermission)
            }
        }
        val existingCodes = existingPermissions.map { it.code }.toSet()
        val missing = PermissionCatalog.definitions
            .filterNot { it.code in existingCodes }
            .map { definition ->
                Permission().apply {
                    code = definition.code
                    name = definition.name
                    enabled = true
                }
            }
        if (missing.isNotEmpty()) permissionRepository.saveAll(missing)
        val allPermissions = permissionRepository.findAll().associateBy { it.code }
        val superAdmin = roleRepository.findByNameIgnoreCase(SecurityRole.SUPER_ADMIN)
        if (superAdmin != null) {
            val permissionCount = superAdmin.permissions.size
            superAdmin.permissions.addAll(allPermissions.values)
            if (superAdmin.permissions.size != permissionCount) roleRepository.save(superAdmin)
        }
        val admin = roleRepository.findByNameIgnoreCase(SecurityRole.ADMIN)
        if (admin != null && admin.permissions.isEmpty()) {
            admin.permissions = allPermissions.values
                .filterNot { it.code in SUPER_ADMIN_ONLY_PERMISSION_CODES }
                .toMutableSet()
            roleRepository.save(admin)
        }
        if (admin != null) {
            val monitorPermission = allPermissions[PermissionCatalog.SYSTEM_MONITOR_READ]
            if (monitorPermission != null && admin.permissions.add(monitorPermission)) roleRepository.save(admin)
            // 新增的同步权限需要补授给已有权限配置的 ADMIN 角色，避免存量环境看不到新功能。
            val ensured = allPermissions.filterKeys { it in ADMIN_ENSURED_PERMISSION_CODES }.values
            if (ensured.any { admin.permissions.add(it) }) roleRepository.save(admin)
        }
        val deptAdmin = roleRepository.findByNameIgnoreCase(SecurityRole.DEPT_ADMIN)
        if (deptAdmin != null && deptAdmin.permissions.isEmpty()) {
            deptAdmin.permissions = allPermissions.values
                .filterNot { it.code in DEPT_ADMIN_DENIED_PERMISSION_CODES }
                .toMutableSet()
            roleRepository.save(deptAdmin)
        }
        if (deptAdmin != null) {
            // 后补进入字典的权限同样要补授，避免存量环境里部门管理看不到新功能。
            val ensured = allPermissions.filterKeys { it in DEPT_ADMIN_ENSURED_PERMISSION_CODES }.values
            if (ensured.any { deptAdmin.permissions.add(it) }) roleRepository.save(deptAdmin)
        }
        val user = roleRepository.findByNameIgnoreCase(SecurityRole.USER)
        if (user != null && user.permissions.isEmpty()) {
            user.permissions = USER_PERMISSION_CODES.mapNotNull { allPermissions[it] }.toMutableSet()
            roleRepository.save(user)
        }
        if (user != null) {
            // 后补的普通用户可读权限必须补授，否则存量环境里普通用户看不到自己的进出记录。
            val ensured = allPermissions.filterKeys { it in USER_PERMISSION_CODES }.values
            if (ensured.any { user.permissions.add(it) }) roleRepository.save(user)
        }
    }

    private companion object {
        /** 平台管理（ADMIN）角色不授予的治理类权限。 */
        val SUPER_ADMIN_ONLY_PERMISSION_CODES = setOf(
            "role:manage",
            "permission:manage",
            "user:role-assign",
            "audit:delete",
        )

        /** 后补进入权限字典的能力，即使 ADMIN 角色已有权限配置也必须补授。 */
        val ADMIN_ENSURED_PERMISSION_CODES = setOf("owner:sync", "account:sync", "sync-history:read")

        /**
         * 部门管理不授予的权限，采用排除法以便新增权限码时自动纳入。
         *
         * device:read 与 position:read 一并排除是刻意的：Device 与 Position 表在数据模型上
         * 没有任何部门关联字段，授予只读只会得到一个要么全量泄露、要么恒为空的接口。
         */
        val DEPT_ADMIN_DENIED_PERMISSION_CODES = setOf(
            "role:manage",
            "permission:manage",
            "user:role-assign",
            "user:disable",
            "department:manage",
            "audit:read",
            "audit:export",
            "audit:verify",
            "audit:delete",
            "system-monitor:read",
            "device:read",
            "device:manage",
            "position:read",
            "dictionary:manage",
        )

        /** 后补进入权限字典、需要补授给已有部门管理角色的能力。 */
        val DEPT_ADMIN_ENSURED_PERMISSION_CODES = setOf(
            "vehicle-record:read",
            "person-record:read",
            "owner:sync",
            "account:sync",
            "sync-history:read",
        )

        /**
         * 普通用户的自助读取能力。
         *
         * 只给"查看"类权限，且这些接口都会按本人范围过滤（普通用户看到的是自己名下的车牌与
         * 自己的门禁身份），因此不构成越权。管理类权限一律不给。
         */
        val USER_PERMISSION_CODES = setOf(
            "dashboard:read",
            "vehicle-record:read",
            "person-record:read",
        )
    }
}
