package top.foxball.cartask.authentication

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import top.foxball.cartask.entity.User
import top.foxball.cartask.audit.AuditAction
import top.foxball.cartask.audit.AuditCommand
import top.foxball.cartask.audit.AuditService
import top.foxball.cartask.repository.UserRepository
import java.time.Duration
import java.time.LocalDateTime

interface AuthService {
    data class LoginCommand(
        @param:JsonProperty("username") val username: kotlin.String,
        @param:JsonProperty("password") val password: CredentialValue,
        @param:JsonProperty("captchaToken") val captchaToken: kotlin.String?,
        @param:JsonProperty("captchaAnswer") val captchaAnswer: kotlin.String?,
    )
    
    data class LoginData(
        val accessToken: AccessTokenValue,
        val expiresAt: LocalDateTime,
        val userId: Long,
        val username: kotlin.String,
        val role: kotlin.String,
        val permissions: Set<kotlin.String>,
        /** 初始密码尚未修改，前端需强制进入改密页；服务端另有过滤器兜底拦截。 */
        val mustChangePassword: Boolean = false,
        val avatar: kotlin.String? = null,
    )

    data class SmsSendCommand(
        @param:JsonProperty("phone") val phone: kotlin.String,
        @param:JsonProperty("purpose") val purpose: kotlin.String?,
        @param:JsonProperty("captchaToken") val captchaToken: kotlin.String?,
        @param:JsonProperty("captchaAnswer") val captchaAnswer: kotlin.String?,
    )

    data class SmsLoginCommand(
        @param:JsonProperty("phone") val phone: kotlin.String,
        @param:JsonProperty("code") val code: kotlin.String,
    )

    data class ResetPasswordCommand(val phone: String, val code: String, val newPassword: CredentialValue)

    fun login(command: LoginCommand): LoginData
    fun sendSmsCode(command: SmsSendCommand)
    fun loginBySms(command: SmsLoginCommand): LoginData
    fun resetPassword(command: ResetPasswordCommand)
    fun logout(tokenId: kotlin.String)
}

@Service
class AuthServiceImpl(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtTokenService: JwtTokenService,
    private val sessionRepository: RedisTokenSessionRepository,
    private val captchaService: CaptchaService,
    private val loginAttemptLimiter: LoginAttemptLimiter,
    private val rolePermissionService: RolePermissionService,
    private val auditService: AuditService? = null,
    private val smsVerificationService: SmsVerificationService,
) : AuthService {
    override fun login(command: AuthService.LoginCommand): AuthService.LoginData {
        // 验证码先于凭据校验，与原型一致：验证码错误不计入登录失败次数。
        captchaService.verify(command.captchaToken, command.captchaAnswer)
        loginAttemptLimiter.check(command.username)
        val user: User
        val role: String
        try {
            user = userRepository.findByUsername(command.username)
                ?: throw BadCredentialsException("用户名或密码错误")
            if (!passwordEncoder.matches(command.password, user.passwordHash)) {
                throw BadCredentialsException("用户名或密码错误")
            }
            if (!user.enabled || user.status != User.Status.Activity) {
                throw BadCredentialsException("用户名或密码错误")
            }
            role = SecurityRole.normalizeOrNull(user.role)
                ?: throw BadCredentialsException("用户名或密码错误")
        } catch (ex: BadCredentialsException) {
            loginAttemptLimiter.recordFailure(command.username)
            runCatching {
                auditService?.record(
                    AuditCommand(
                        AuditAction.AUTH_LOGIN_FAILED,
                        "user",
                        targetId = command.username.take(128),
                        result = top.foxball.cartask.entity.AuditEvent.Result.FAILED,
                        reasonCode = "INVALID_CREDENTIALS",
                        targetSummary = mapOf("username" to command.username.take(128)),
                    ),
                )
            }
            throw ex
        }
        loginAttemptLimiter.clear(command.username)
        val permissions = rolePermissionService.permissionsFor(role)
        val userId = user.id ?: throw IllegalStateException("用户 ID 缺失")
        val version = sessionRepository.currentTokenVersion(userId)
        val issued = jwtTokenService.issue(userId, user.username, role, version)
        sessionRepository.save(
            issued.tokenId,
            RedisTokenSession(
                userId = userId,
                username = user.username,
                role = role,
                tokenVersion = version,
                tokenHash = issued.tokenHash,
                tokenCiphertext = issued.tokenCiphertext,
                tokenEncryptionKeyId = issued.tokenEncryptionKeyId,
                sessionSchemaVersion = SESSION_SCHEMA_VERSION,
                issuedAt = issued.issuedAt,
                expiresAt = issued.expiresAt,
                mustChangePassword = user.mustChangePassword,
            ),
            Duration.between(issued.issuedAt, issued.expiresAt),
        )
        runCatching {
            auditService?.record(
                AuditCommand(
                    AuditAction.AUTH_LOGIN_SUCCEEDED,
                    "user",
                    userId.toString(),
                    targetSummary = mapOf("username" to user.username, "role" to role),
                ),
            )
        }
        return AuthService.LoginData(
            issued.accessToken,
            issued.expiresAt,
            userId,
            user.username,
            role,
            permissions,
            user.mustChangePassword,
            user.avatar,
        )
    }

    override fun sendSmsCode(command: AuthService.SmsSendCommand) {
        val purpose = when (val raw = command.purpose?.trim()?.uppercase()) {
            null, "", "LOGIN" -> SmsVerificationService.Purpose.LOGIN
            "RESET_PASSWORD", "RESET" -> SmsVerificationService.Purpose.RESET_PASSWORD
            else -> throw IllegalArgumentException("短信验证码用途无效")
        }
        // 图形验证码先于发送校验并一次性作废，未通过校验时不产生任何短信费用。
        captchaService.verify(command.captchaToken, command.captchaAnswer)
        smsVerificationService.send(command.phone, purpose)
    }

    override fun loginBySms(command: AuthService.SmsLoginCommand): AuthService.LoginData {
        smsVerificationService.verify(command.phone, command.code, SmsVerificationService.Purpose.LOGIN)
        val user = userRepository.findByPhone(command.phone.trim())
            ?: throw BadCredentialsException("手机号或验证码错误")
        if (!user.enabled || user.status != User.Status.Activity) throw BadCredentialsException("手机号或验证码错误")
        val role = SecurityRole.normalizeOrNull(user.role) ?: throw BadCredentialsException("手机号或验证码错误")
        return issueToken(user, role)
    }

    override fun resetPassword(command: AuthService.ResetPasswordCommand) {
        smsVerificationService.verify(command.phone, command.code, SmsVerificationService.Purpose.RESET_PASSWORD)
        require(command.newPassword.isNotBlank()) { "新密码不能为空" }
        val user = userRepository.findByPhone(command.phone.trim()) ?: throw BadCredentialsException("手机号或验证码错误")
        user.passwordHash = passwordEncoder.encode(command.newPassword).toString()
        // 用户已自行设定新密码，初始密码不再有效，「必须改密」随之解除。
        user.mustChangePassword = false
        user.updatedAt = LocalDateTime.now()
        userRepository.save(user)
        user.id?.let(sessionRepository::incrementTokenVersion)
    }

    private fun issueToken(user: User, role: String): AuthService.LoginData {
        val permissions = rolePermissionService.permissionsFor(role)
        val userId = user.id ?: throw IllegalStateException("用户 ID 缺失")
        val version = sessionRepository.currentTokenVersion(userId)
        val issued = jwtTokenService.issue(userId, user.username, role, version)
        sessionRepository.save(
            issued.tokenId,
            RedisTokenSession(
                userId,
                user.username,
                role,
                version,
                issued.tokenHash,
                issued.tokenCiphertext,
                issued.tokenEncryptionKeyId,
                SESSION_SCHEMA_VERSION,
                issued.issuedAt,
                issued.expiresAt,
                user.mustChangePassword,
            ),
            Duration.between(issued.issuedAt, issued.expiresAt),
        )
        return AuthService.LoginData(
            issued.accessToken,
            issued.expiresAt,
            userId,
            user.username,
            role,
            permissions,
            user.mustChangePassword,
            user.avatar,
        )
    }
    
    override fun logout(tokenId: kotlin.String) {
        sessionRepository.delete(tokenId)
        runCatching {
            auditService?.record(
                AuditCommand(
                    AuditAction.AUTH_LOGOUT,
                    "session",
                    reasonCode = "USER_LOGOUT",
                    targetSummary = mapOf("token_id_hash" to tokenId.hashCode().toString(16)),
                ),
            )
        }
    }

    private companion object {
        /** 会话正文结构版本；新增 must_change_password 字段后升到 2，旧会话缺字段时按 false 解析。 */
        const val SESSION_SCHEMA_VERSION = 2
    }
}
