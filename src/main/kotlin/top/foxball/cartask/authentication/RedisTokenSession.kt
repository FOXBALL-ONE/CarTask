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
    /**
     * 该会话是否仍处于「必须先改密码」状态，供每个请求就地判定，
     * 避免为此在校验链路上多查一次数据库。旧会话缺少该字段时按 false 解析。
     */
    @param:JsonProperty("must_change_password") val mustChangePassword: Boolean = false,
    /**
     * 当前工作部门；null 表示不限部门（「全部」）。
     *
     * 只存在于会话，刻意不进 JWT：切换工作部门时不必重新签发 token，也不会因为
     * token 与会话不一致被 [JwtAuthenticationFilter] 的一致性校验拒绝。
     * 旧会话缺少该字段时按 null 解析，即与引入该字段之前的行为一致。
     */
    @param:JsonProperty("working_department_id") val workingDepartmentId: Long? = null,
)
