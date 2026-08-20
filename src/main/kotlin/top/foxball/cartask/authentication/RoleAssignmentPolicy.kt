package top.foxball.cartask.authentication

import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

/** 防止普通管理员授予或修改管理员及超级管理员角色。 */
@Component
class RoleAssignmentPolicy {
    private val protectedRoles = setOf("SUPER_ADMIN", "ADMIN")

    fun validateAssignment(role: String) {
        val normalizedRole = SecurityRole.normalize(role)
        if (normalizedRole !in protectedRoles) return

        requireSuperAdmin()
    }

    fun validateManagement(roles: Collection<String>) {
        if (roles.none { SecurityRole.normalize(it) in protectedRoles }) return

        requireSuperAdmin()
    }

    private fun requireSuperAdmin() {
        val authentication = SecurityContextHolder.getContext().authentication
        if (authentication?.authorities?.any { it.authority == SecurityRole.authority("SUPER_ADMIN") } != true) {
            throw AccessDeniedException("只有超级管理员可以管理管理员角色")
        }
    }
}
