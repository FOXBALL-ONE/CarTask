package top.foxball.cartask.authentication

/**
 * LoginRateLimitProperties：认证子系统中的组件，负责实现相关安全、令牌或访问控制能力。
 *
 * 该文件中的类型和函数用于支撑登录认证流程，并在边界处校验输入与会话状态。
 */

/**
 * LoginRateLimitProperties 认证组件说明。
 *
 * 该文件集中定义认证流程所需的领域类型、服务及基础设施适配逻辑。
 */

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration


@ConfigurationProperties(prefix = "cartask.security.login-rate-limit")
/** data class LoginRateLimitProperties：用于认证领域的类型，封装相关状态与行为。 */
data class LoginRateLimitProperties(
    val enabled: Boolean = true,
    val maxAttempts: Long = 5,
    val window: Duration = Duration.ofMinutes(15),
) {
    
    
    /**
     * validate 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    /** validate：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
    fun validate() {
        require(maxAttempts > 0) { "登录限流最大尝试次数必须大于 0" }
        require(window.isPositive) { "登录限流窗口必须大于 0" }
    }
}


