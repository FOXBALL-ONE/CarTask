package top.foxball.cartask.authentication

/**
 * SecurityPermission：认证子系统中的组件，负责实现相关安全、令牌或访问控制能力。
 *
 * 该文件中的类型和函数用于支撑登录认证流程，并在边界处校验输入与会话状态。
 */

/**
 * SecurityPermission 认证组件说明。
 *
 * 该文件集中定义认证流程所需的领域类型、服务及基础设施适配逻辑。
 */

import java.util.*


/**
 * SecurityPermission 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
/** object SecurityPermission：用于认证领域的类型，封装相关状态与行为。 */
object SecurityPermission {
    private val codePattern = Regex("[a-z][a-z0-9_-]*:[a-z][a-z0-9_-]*")
    
    
    /**
     * normalize 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    /** normalize：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
    fun normalize(value: String): String {
        val permission = value.trim().lowercase(Locale.ROOT)
        require(codePattern.matches(permission)) { "不支持的权限编码" }
        return permission
    }
    
    
    /**
     * normalizeOrNull 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    /** normalizeOrNull：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
    fun normalizeOrNull(value: String): String? = runCatching { normalize(value) }.getOrNull()
}


