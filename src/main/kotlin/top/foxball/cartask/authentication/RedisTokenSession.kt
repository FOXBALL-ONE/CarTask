package top.foxball.cartask.authentication

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime

/** Redis 会话正文。token 仅以 AES-GCM 密文保存，认证时使用 tokenHash 常量时间比对。 */
data class RedisTokenSession(
    @param:JsonProperty("user_id") val userId: Long,
    val username: String,
    val role: String,
    @param:JsonProperty("token_version") val tokenVersion: Long,
    @param:JsonProperty("token_hash") val tokenHash: String,
    @param:JsonProperty("token_ciphertext") val tokenCiphertext: String,
    @param:JsonProperty("token_encryption_key_id") val tokenEncryptionKeyId: String,
    @param:JsonProperty("session_schema_version") val sessionSchemaVersion: Int,
    @param:JsonProperty("issued_at") val issuedAt: LocalDateTime,
    @param:JsonProperty("expires_at") val expiresAt: LocalDateTime,
)
