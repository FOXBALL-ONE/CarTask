package top.foxball.cartask.authentication

/**
 * RedisTokenSession：认证子系统中的组件，负责实现相关安全、令牌或访问控制能力。
 *
 * 该文件中的类型和函数用于支撑登录认证流程，并在边界处校验输入与会话状态。
 */

/**
 * RedisTokenSession 认证组件说明。
 *
 * 该文件集中定义认证流程所需的领域类型、服务及基础设施适配逻辑。
 */

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime


/** data class RedisTokenSession：用于认证领域的类型，封装相关状态与行为。 */
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
    
    
    @param:JsonProperty("must_change_password") val mustChangePassword: Boolean = false,
    
    
    @param:JsonProperty("working_department_id") val workingDepartmentId: Long? = null,
)


