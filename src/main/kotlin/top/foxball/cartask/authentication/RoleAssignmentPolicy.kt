package top.foxball.cartask.authentication

import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

/**
 * 防止普通管理员授予或修改管理员及超级管理员角色。
 *
 * 部门管理持有 user:create / owner:manage 等权限，而 Excel 导入与用户创建都会把请求里的
 * 角色字符串交给这里校验，因此 DEPT_ADMIN 必须与 ADMIN 同等受保护，否则是一条直接的提权路径。
 */
@Component
class RoleAssignmentPolicy {
    private val protectedRoles = setOf(SecurityRole.SUPER_ADMIN, SecurityRole.ADMIN, SecurityRole.DEPT_ADMIN)

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
