package top.foxball.cartask.authentication

/**
 * RoleAssignmentPolicy：认证子系统中的组件，负责实现相关安全、令牌或访问控制能力。
 *
 * 该文件中的类型和函数用于支撑登录认证流程，并在边界处校验输入与会话状态。
 */

/**
 * RoleAssignmentPolicy 认证组件说明。
 *
 * 该文件集中定义认证流程所需的领域类型、服务及基础设施适配逻辑。
 */

import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component


@Component
/**
 * RoleAssignmentPolicy 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
class RoleAssignmentPolicy {
    private val protectedRoles = setOf(SecurityRole.SUPER_ADMIN, SecurityRole.ADMIN, SecurityRole.DEPT_ADMIN)
    
    
    /**
     * validateAssignment 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    /** validateAssignment：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
    fun validateAssignment(role: String) {
        val normalizedRole = SecurityRole.normalize(role)
        if (normalizedRole !in protectedRoles) return
        
        requireSuperAdmin()
    }
    
    
    /**
     * validateManagement 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    /** validateManagement：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
    fun validateManagement(roles: Collection<String>) {
        if (roles.none { SecurityRole.normalize(it) in protectedRoles }) return
        
        requireSuperAdmin()
    }
    
    
    /** requireSuperAdmin：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
    private fun requireSuperAdmin() {
        val authentication = SecurityContextHolder.getContext().authentication
        if (authentication?.authorities?.any { it.authority == SecurityRole.authority("SUPER_ADMIN") } != true) {
            throw AccessDeniedException("只有超级管理员可以管理管理员角色")
        }
    }
}


