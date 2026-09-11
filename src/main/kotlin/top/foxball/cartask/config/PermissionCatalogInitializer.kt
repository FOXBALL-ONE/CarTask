package top.foxball.cartask.config

import jakarta.transaction.Transactional
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import top.foxball.cartask.authentication.PermissionCatalog
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
        val superAdmin = roleRepository.findByNameIgnoreCase("SUPER_ADMIN")
        if (superAdmin != null) {
            val permissionCount = superAdmin.permissions.size
            superAdmin.permissions.addAll(allPermissions.values)
            if (superAdmin.permissions.size != permissionCount) roleRepository.save(superAdmin)
        }
        val admin = roleRepository.findByNameIgnoreCase("ADMIN")
        if (admin != null && admin.permissions.isEmpty()) {
            admin.permissions = allPermissions.values
                .filterNot { it.code in setOf("role:manage", "permission:manage", "user:role-assign", "audit:delete") }
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
        val user = roleRepository.findByNameIgnoreCase("USER")
        if (user != null && user.permissions.isEmpty()) {
            user.permissions = listOfNotNull(allPermissions["dashboard:read"]).toMutableSet()
            roleRepository.save(user)
        }
    }

    private companion object {
        /** 后补进入权限字典的能力，即使 ADMIN 角色已有权限配置也必须补授。 */
        val ADMIN_ENSURED_PERMISSION_CODES = setOf("account:sync", "sync-history:read")
    }
}
