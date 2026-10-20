package top.foxball.cartask.authentication

/**
 * AuthService：认证子系统中的组件，负责实现相关安全、令牌或访问控制能力。
 *
 * 该文件中的类型和函数用于支撑登录认证流程，并在边界处校验输入与会话状态。
 */

/**
 * AuthService 认证组件说明。
 *
 * 该文件集中定义认证流程所需的领域类型、服务及基础设施适配逻辑。
 */

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import top.foxball.cartask.audit.AuditAction
import top.foxball.cartask.audit.AuditCommand
import top.foxball.cartask.audit.AuditService
import top.foxball.cartask.entity.User
import top.foxball.cartask.repository.UserResourcePermissionRepository
import top.foxball.cartask.repository.UserManagedDepartmentRepository
import java.time.Duration
import java.time.LocalDateTime

/**
 * AuthService 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
/** interface AuthService：用于认证领域的类型，封装相关状态与行为。 */
interface AuthService {
    /**
     * LoginCommand 的职责与行为说明。
     * 该声明负责认证域中的相关数据处理、校验或服务调用。
     */
    data class LoginCommand(
        @param:JsonProperty("username") val username: String,
        @param:JsonProperty("password") val password: CredentialValue,
        @param:JsonProperty("captchaToken") val captchaToken: String?,
        @param:JsonProperty("captchaAnswer") val captchaAnswer: String?,
    )
    
    /**
     * LoginData 的职责与行为说明。
     * 该声明负责认证域中的相关数据处理、校验或服务调用。
     */
    data class LoginData(
        val accessToken: AccessTokenValue,
        val expiresAt: LocalDateTime,
        val userId: Long,
        val username: String,
        val role: String,
        val permissions: Set<String>,
        @param:JsonProperty("resource_permissions") val resourcePermissions: Set<String> = emptySet(),
        
        val mustChangePassword: Boolean = false,
        val avatar: String? = null,
        
        val workingDepartmentId: Long? = null,
    )
    
    /**
     * SmsSendCommand 的职责与行为说明。
     * 该声明负责认证域中的相关数据处理、校验或服务调用。
     */
    data class SmsSendCommand(
        @param:JsonProperty("phone") val phone: String,
        @param:JsonProperty("purpose") val purpose: String?,
        @param:JsonProperty("captchaToken") val captchaToken: String?,
        @param:JsonProperty("captchaAnswer") val captchaAnswer: String?,
    )
    
    /**
     * SmsLoginCommand 的职责与行为说明。
     * 该声明负责认证域中的相关数据处理、校验或服务调用。
     */
    data class SmsLoginCommand(
        @param:JsonProperty("phone") val phone: String,
        @param:JsonProperty("code") val code: String,
    )
    
    /**
     * ResetPasswordCommand 的职责与行为说明。
     * 该声明负责认证域中的相关数据处理、校验或服务调用。
     */
    data class ResetPasswordCommand(val phone: String, val code: String, val newPassword: CredentialValue)
    
    
    /**
     * login 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    /** login：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
    fun login(command: LoginCommand): LoginData
    
    
    /**
     * sendSmsCode 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    /** sendSmsCode：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
    fun sendSmsCode(command: SmsSendCommand)
    
    
    /**
     * loginBySms 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    /** loginBySms：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
    fun loginBySms(command: SmsLoginCommand): LoginData
    
    
    /**
     * resetPassword 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    /** resetPassword：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
    fun resetPassword(command: ResetPasswordCommand)
    
    
    /**
     * logout 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    /** logout：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
    fun logout(tokenId: String)
}

@Service
/**
 * AuthServiceImpl 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
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
    private val userResourcePermissionRepository: UserResourcePermissionRepository? = null,
    private val userManagedDepartmentRepository: UserManagedDepartmentRepository? = null,
) : AuthService {
    
    
    /** login：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
    override fun login(command: AuthService.LoginCommand): AuthService.LoginData {
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
        val userId = user.id ?: throw IllegalStateException("用户 ID 缺失")
        val permissions = permissionsFor(role, userId)
        val resourcePermissions = resourcePermissionsOf(userId)
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
                workingDepartmentId = defaultWorkingDepartment(user, role, userId, resourcePermissions),
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
            resourcePermissions,
            user.mustChangePassword,
            user.avatar,
            defaultWorkingDepartment(user, role, userId, resourcePermissions),
        )
    }
    
    
    /** sendSmsCode：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
    override fun sendSmsCode(command: AuthService.SmsSendCommand) {
        val purpose = when (val raw = command.purpose?.trim()?.uppercase()) {
            null, "", "LOGIN" -> SmsVerificationService.Purpose.LOGIN
            "RESET_PASSWORD", "RESET" -> SmsVerificationService.Purpose.RESET_PASSWORD
            "CHANGE_PHONE" -> SmsVerificationService.Purpose.CHANGE_PHONE
            else -> throw IllegalArgumentException("短信验证码用途无效")
        }
        if (!smsVerificationService.verificationSkipped) {
            captchaService.verify(command.captchaToken, command.captchaAnswer)
        }
        smsVerificationService.send(command.phone, purpose)
    }
    
    
    /** loginBySms：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
    override fun loginBySms(command: AuthService.SmsLoginCommand): AuthService.LoginData {
        smsVerificationService.verify(command.phone, command.code, SmsVerificationService.Purpose.LOGIN)
        val user = userRepository.findByPhone(command.phone.trim())
            ?: throw BadCredentialsException("手机号或验证码错误")
        if (!user.enabled || user.status != User.Status.Activity) throw BadCredentialsException("手机号或验证码错误")
        val role = SecurityRole.normalizeOrNull(user.role) ?: throw BadCredentialsException("手机号或验证码错误")
        return issueToken(user, role)
    }
    
    
    /** resetPassword：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
    override fun resetPassword(command: AuthService.ResetPasswordCommand) {
        PasswordPolicy.errorMessage(command.newPassword, "新密码")?.let { throw IllegalArgumentException(it) }
        smsVerificationService.verify(command.phone, command.code, SmsVerificationService.Purpose.RESET_PASSWORD)
        val user =
            userRepository.findByPhone(command.phone.trim()) ?: throw BadCredentialsException("手机号或验证码错误")
        user.passwordHash = passwordEncoder.encode(command.newPassword).toString()
        user.mustChangePassword = false
        user.updatedAt = LocalDateTime.now()
        userRepository.save(user)
        user.id?.let(sessionRepository::incrementTokenVersion)
    }
    
    
    /** issueToken：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
    private fun issueToken(user: User, role: String): AuthService.LoginData {
        val userId = user.id ?: throw IllegalStateException("用户 ID 缺失")
        val permissions = permissionsFor(role, userId)
        val resourcePermissions = resourcePermissionsOf(userId)
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
                workingDepartmentId = defaultWorkingDepartment(user, role, userId, resourcePermissions),
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
            resourcePermissions,
            user.mustChangePassword,
            user.avatar,
            defaultWorkingDepartment(user, role, userId, resourcePermissions),
        )
    }
    
    
    /** defaultWorkingDepartment：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
    private fun defaultWorkingDepartment(user: User, role: String, userId: Long, resourcePermissions: Set<String>): Long? = when {
        role == SecurityRole.DEPT_ADMIN -> user.department?.id
        role == SecurityRole.USER && resourcePermissions.isNotEmpty() -> {
            val departments = userManagedDepartmentRepository?.findDepartmentIds(userId).orEmpty().toSet() +
                    setOfNotNull(user.department?.id)
            departments.singleOrNull()
        }
        else -> null
    }

    private fun resourcePermissionsOf(userId: Long): Set<String> =
        userResourcePermissionRepository?.findByUserId(userId)?.map { it.resourceType.wireName }?.toSet().orEmpty()

    private fun permissionsFor(role: String, userId: Long): Set<String> =
        if (userResourcePermissionRepository == null) rolePermissionService.permissionsFor(role)
        else rolePermissionService.permissionsFor(role, userId)
    
    
    /** logout：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
    override fun logout(tokenId: String) {
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
        
        const val SESSION_SCHEMA_VERSION = 3
    }
}


