package top.foxball.cartask.authentication

/**
 * AuthenticationExceptions：认证子系统中的组件，负责实现相关安全、令牌或访问控制能力。
 *
 * 该文件中的类型和函数用于支撑登录认证流程，并在边界处校验输入与会话状态。
 */

/**
 * AuthenticationExceptions 认证组件说明。
 *
 * 该文件集中定义认证流程所需的领域类型、服务及基础设施适配逻辑。
 */

import org.springframework.security.core.AuthenticationException


/**
 * JwtAuthenticationException 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
class JwtAuthenticationException : AuthenticationException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}


/**
 * AuthenticationInfrastructureException 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
class AuthenticationInfrastructureException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)


/**
 * LoginRateLimitException 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
class LoginRateLimitException(
    val retryAfterSeconds: Long,
) : RuntimeException("登录尝试过于频繁，请稍后重试")


