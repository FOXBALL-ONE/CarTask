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

    /**
     * validateAssignment：校验输入、状态或访问条件。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param role 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun validateAssignment(role: String) {
        val normalizedRole = SecurityRole.normalize(role)
        if (normalizedRole !in protectedRoles) return

        requireSuperAdmin()
    }

    /**
     * validateManagement：校验输入、状态或访问条件。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param roles 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun validateManagement(roles: Collection<String>) {
        if (roles.none { SecurityRole.normalize(it) in protectedRoles }) return

        requireSuperAdmin()
    }

    /**
     * requireSuperAdmin：校验输入、状态或访问条件。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    private fun requireSuperAdmin() {
        val authentication = SecurityContextHolder.getContext().authentication
        if (authentication?.authorities?.any { it.authority == SecurityRole.authority("SUPER_ADMIN") } != true) {
            throw AccessDeniedException("只有超级管理员可以管理管理员角色")
        }
    }
}
