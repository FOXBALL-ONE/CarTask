package top.foxball.cartask.authentication

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/** JWT 签名、校验及 Redis 中 token 密文副本的配置。所有密钥均使用 Base64 编码。 */
@ConfigurationProperties(prefix = "shopmall.security.jwt")
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
