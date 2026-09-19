package top.foxball.cartask.config

/**
 * PermissionCatalogInitializer 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */

import jakarta.transaction.Transactional
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import top.foxball.cartask.authentication.PermissionCatalog
import top.foxball.cartask.authentication.SecurityRole
import top.foxball.cartask.entity.Permission
import top.foxball.cartask.entity.Role
import top.foxball.cartask.repository.PermissionRepository
import top.foxball.cartask.repository.RoleRepository


@Component
/**
 * PermissionCatalogInitializer 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
class PermissionCatalogInitializer(
    private val permissionRepository: PermissionRepository,
    private val roleRepository: RoleRepository,
) {
    @Order(Ordered.LOWEST_PRECEDENCE)
    @EventListener(classes = [ApplicationReadyEvent::class])
    @Transactional
            
            
            /**
             * write 函数：执行与该组件职责相关的业务操作。
             * 参数和返回值遵循调用方与领域服务之间的约定。
             */
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
            val ensured = allPermissions.filterKeys { it in ADMIN_ENSURED_PERMISSION_CODES }.values
            if (grantMissing(admin, ensured)) roleRepository.save(admin)
        }
        val deptAdmin = roleRepository.findByNameIgnoreCase(SecurityRole.DEPT_ADMIN)
        if (deptAdmin != null && deptAdmin.permissions.isEmpty()) {
            deptAdmin.permissions = allPermissions.values
                .filterNot { it.code in DEPT_ADMIN_DENIED_PERMISSION_CODES }
                .toMutableSet()
            roleRepository.save(deptAdmin)
        }
        if (deptAdmin != null) {
            val ensured = allPermissions.filterKeys { it in DEPT_ADMIN_ENSURED_PERMISSION_CODES }.values
            if (grantMissing(deptAdmin, ensured)) roleRepository.save(deptAdmin)
        }
        val user = roleRepository.findByNameIgnoreCase(SecurityRole.USER)
        if (user != null && user.permissions.isEmpty()) {
            user.permissions = USER_PERMISSION_CODES.mapNotNull { allPermissions[it] }.toMutableSet()
            roleRepository.save(user)
        }
        if (user != null) {
            val ensured = allPermissions.filterKeys { it in USER_PERMISSION_CODES }.values
            if (grantMissing(user, ensured)) roleRepository.save(user)
        }
    }
    
    
    private fun grantMissing(role: Role, permissions: Collection<Permission>): Boolean {
        var changed = false
        permissions.forEach { permission ->
            if (role.permissions.add(permission)) changed = true
        }
        return changed
    }
    
    private companion object {
        
        val SUPER_ADMIN_ONLY_PERMISSION_CODES = setOf(
            "role:manage",
            "permission:manage",
            "user:role-assign",
            "audit:delete",
            "backup:manage",
            "online-user:logout",
            "system-config:read",
            "system-config:write",
        )
        
        
        val ADMIN_ENSURED_PERMISSION_CODES = setOf(
            "owner:sync",
            "account:sync",
            "sync-history:read",
            "sync-schedule:manage",
            "gate-person:review",
            "gate-person:export",
            "vehicle-inout-request:read",
            "vehicle-inout-request:apply",
            "vehicle-inout-request:review",
            "vehicle-inout-request:sync",
            "plate:sync:retry",
            "plate:sync:reconcile",
        )
        
        
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
            "backup:manage",
            "sync-schedule:manage",
            "online-user:logout",
        )
        
        
        val DEPT_ADMIN_ENSURED_PERMISSION_CODES = setOf(
            "vehicle-record:read",
            "person-record:read",
            "owner:sync",
            "account:sync",
            "sync-history:read",
            "gate-person:review",
            "gate-person:export",
            "vehicle-inout-request:read",
            "vehicle-inout-request:apply",
            "vehicle-inout-request:review",
            "vehicle-inout-request:sync",
            "plate:sync:retry",
        )
        
        
        val USER_PERMISSION_CODES = setOf(
            "dashboard:read",
            "vehicle-record:read",
            "person-record:read",
            "file:read",
        )
    }
}


