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
    )
    
    data class LoginData(
        val accessToken: AccessTokenValue,
        val expiresAt: LocalDateTime,
        val userId: Long,
        val username: kotlin.String,
        val role: kotlin.String,
    )
    
    fun login(command: LoginCommand): LoginData
    fun logout(tokenId: kotlin.String)
}

@Service
class AuthServiceImpl(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtTokenService: JwtTokenService,
    private val sessionRepository: RedisTokenSessionRepository,
    private val loginAttemptLimiter: LoginAttemptLimiter,
    private val auditService: AuditService? = null,
) : AuthService {
    override fun login(command: AuthService.LoginCommand): AuthService.LoginData {
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
                sessionSchemaVersion = 1,
                issuedAt = issued.issuedAt,
                expiresAt = issued.expiresAt,
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
        return AuthService.LoginData(issued.accessToken, issued.expiresAt, userId, user.username, role)
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
}
