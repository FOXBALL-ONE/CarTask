package top.foxball.cartask.authentication

/**
 * JwtProperties：认证子系统中的组件，负责实现相关安全、令牌或访问控制能力。
 *
 * 该文件中的类型和函数用于支撑登录认证流程，并在边界处校验输入与会话状态。
 */

/**
 * JwtProperties 认证组件说明。
 *
 * 该文件集中定义认证流程所需的领域类型、服务及基础设施适配逻辑。
 */

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration


@ConfigurationProperties(prefix = "cartask.security.jwt")
/** data class JwtProperties：用于认证领域的类型，封装相关状态与行为。 */
data class JwtProperties(
    val issuer: String = "",
    val audience: String = "",
    val activeSigningKeyId: String = "",
    val keys: Map<String, String> = emptyMap(),
    val ttl: Duration = Duration.ofHours(2),
    val clockSkew: Duration = Duration.ofSeconds(30),
    val tokenStorageEncryptionKey: String = "",
    val tokenStorageEncryptionKeyId: String = "",
)


