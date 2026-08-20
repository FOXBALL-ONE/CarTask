package top.foxball.cartask.authentication

import org.springframework.security.core.AuthenticationException

/** Bearer token 不可用、已过期或不再对应有效 Redis 会话。 */
class JwtAuthenticationException : AuthenticationException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}

/** Redis 认证状态不可用；必须 fail-closed，不能退化为仅验 JWT。 */
class AuthenticationInfrastructureException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

/** 登录失败次数超过窗口限制；不泄露用户名是否真实存在。 */
class LoginRateLimitException(
    val retryAfterSeconds: Long,
) : RuntimeException("登录尝试过于频繁，请稍后重试")
