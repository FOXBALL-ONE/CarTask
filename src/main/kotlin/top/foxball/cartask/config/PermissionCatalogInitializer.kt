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
        val existingCodes = permissionRepository.findAll().map { it.code }.toSet()
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
        if (superAdmin != null && superAdmin.permissions.isEmpty()) {
            superAdmin.permissions = allPermissions.values.toMutableSet()
            roleRepository.save(superAdmin)
        }
        val admin = roleRepository.findByNameIgnoreCase("ADMIN")
        if (admin != null && admin.permissions.isEmpty()) {
            admin.permissions = allPermissions.values
                .filterNot { it.code in setOf("role:manage", "permission:manage", "user:role-assign", "audit:delete") }
                .toMutableSet()
            roleRepository.save(admin)
        }
        val user = roleRepository.findByNameIgnoreCase("USER")
        if (user != null && user.permissions.isEmpty()) {
            user.permissions = listOfNotNull(allPermissions["dashboard:read"]).toMutableSet()
            roleRepository.save(user)
        }
    }
}
