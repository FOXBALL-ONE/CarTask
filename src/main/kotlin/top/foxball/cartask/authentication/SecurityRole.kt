package top.foxball.cartask.authentication

/**
 * SecurityRole：认证子系统中的组件，负责实现相关安全、令牌或访问控制能力。
 *
 * 该文件中的类型和函数用于支撑登录认证流程，并在边界处校验输入与会话状态。
 */

/**
 * SecurityRole 认证组件说明。
 *
 * 该文件集中定义认证流程所需的领域类型、服务及基础设施适配逻辑。
 */

import java.util.*


/**
 * SecurityRole 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
/** object SecurityRole：用于认证领域的类型，封装相关状态与行为。 */
object SecurityRole {
    
    const val SUPER_ADMIN = "SUPER_ADMIN"
    
    
    const val ADMIN = "ADMIN"
    
    
    const val DEPT_ADMIN = "DEPT_ADMIN"
    
    
    const val USER = "USER"
    
    private val allowedRoles = setOf(SUPER_ADMIN, ADMIN, DEPT_ADMIN, USER)
    
    
    val ADMIN_ROLES = setOf(SUPER_ADMIN, ADMIN, DEPT_ADMIN)
    
    val SUPER_ADMIN_GOVERNANCE_PERMISSIONS = setOf(
        "role:manage",
        "permission:manage",
        "user:role-assign",
        "user:disable",
    )
    
    
    private val priorities = mapOf(SUPER_ADMIN to 3, ADMIN to 2, DEPT_ADMIN to 1, USER to 0)
    
    
    /**
     * normalize 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    /** normalize：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
    fun normalize(value: String): String {
        val role = value.trim().uppercase(Locale.ROOT).removePrefix("ROLE_")
        require(role in allowedRoles) { "不支持的用户角色" }
        return role
    }
    
    
    /**
     * normalizeOrNull 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    /** normalizeOrNull：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
    fun normalizeOrNull(value: String): String? = runCatching { normalize(value) }.getOrNull()
    
    
    /**
     * authority 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    /** authority：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
    fun authority(value: String): String = "ROLE_${normalize(value)}"
    
    
    /**
     * priorityOf 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    /** priorityOf：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
    fun priorityOf(role: String): Int = priorities[normalize(role)] ?: 0
}


