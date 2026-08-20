package top.foxball.cartask.authentication

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import top.foxball.cartask.entity.User
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
        return AuthService.LoginData(issued.accessToken, issued.expiresAt, userId, user.username, role)
    }
    
    override fun logout(tokenId: kotlin.String) {
        sessionRepository.delete(tokenId)
    }
}
