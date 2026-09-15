package top.foxball.cartask.authentication

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import top.foxball.cartask.audit.AuditAction
import top.foxball.cartask.audit.AuditCommand
import top.foxball.cartask.audit.AuditService
import top.foxball.cartask.entity.User
import top.foxball.cartask.repository.UserRepository
import java.time.Duration
import java.time.LocalDateTime

interface AuthService {
    data class LoginCommand(
        @param:JsonProperty("username") val username: String,
        @param:JsonProperty("password") val password: CredentialValue,
        @param:JsonProperty("captchaToken") val captchaToken: String?,
        @param:JsonProperty("captchaAnswer") val captchaAnswer: String?,
    )

    data class LoginData(
        val accessToken: AccessTokenValue,
        val expiresAt: LocalDateTime,
        val userId: Long,
        val username: String,
        val role: String,
        val permissions: Set<String>,
        /** 初始密码尚未修改，前端需强制进入改密页；服务端另有过滤器兜底拦截。 */
        val mustChangePassword: Boolean = false,
        val avatar: String? = null,
        /** 本次会话的当前工作部门；null 表示不限部门。 */
        val workingDepartmentId: Long? = null,
    )

    data class SmsSendCommand(
        @param:JsonProperty("phone") val phone: String,
        @param:JsonProperty("purpose") val purpose: String?,
        @param:JsonProperty("captchaToken") val captchaToken: String?,
        @param:JsonProperty("captchaAnswer") val captchaAnswer: String?,
    )

    data class SmsLoginCommand(
        @param:JsonProperty("phone") val phone: String,
        @param:JsonProperty("code") val code: String,
    )

    data class ResetPasswordCommand(val phone: String, val code: String, val newPassword: CredentialValue)

    /**
     * login：完成身份认证、令牌或验证码处理。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param command 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun login(command: LoginCommand): LoginData

    /**
     * sendSmsCode：完成身份认证、令牌或验证码处理。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param command 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun sendSmsCode(command: SmsSendCommand)

    /**
     * loginBySms：完成身份认证、令牌或验证码处理。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param command 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun loginBySms(command: SmsLoginCommand): LoginData

    /**
     * resetPassword：更新业务状态或修改相关配置。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param command 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun resetPassword(command: ResetPasswordCommand)

    /**
     * logout：删除、清理或撤销相关数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param tokenId 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun logout(tokenId: String)
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
    /**
     * login：完成身份认证、令牌或验证码处理。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param command 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
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
                workingDepartmentId = defaultWorkingDepartment(user, role),
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
            defaultWorkingDepartment(user, role),
        )
    }

    /**
     * sendSmsCode：完成身份认证、令牌或验证码处理。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param command 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    override fun sendSmsCode(command: AuthService.SmsSendCommand) {
        val purpose = when (val raw = command.purpose?.trim()?.uppercase()) {
            null, "", "LOGIN" -> SmsVerificationService.Purpose.LOGIN
            "RESET_PASSWORD", "RESET" -> SmsVerificationService.Purpose.RESET_PASSWORD
            "CHANGE_PHONE" -> SmsVerificationService.Purpose.CHANGE_PHONE
            else -> throw IllegalArgumentException("短信验证码用途无效")
        }
        // 图形验证码先于发送校验并一次性作废，未通过校验时不产生任何短信费用。
        // 短信验证被临时关闭时连图形验证码一起跳过：这一步存在的意义就是拦住刷短信，
        // 不再发短信时还要先过验证码，与「跳过短信验证」的语义正好相反。
        if (!smsVerificationService.verificationSkipped) {
            captchaService.verify(command.captchaToken, command.captchaAnswer)
        }
        smsVerificationService.send(command.phone, purpose)
    }

    /**
     * loginBySms：完成身份认证、令牌或验证码处理。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param command 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    override fun loginBySms(command: AuthService.SmsLoginCommand): AuthService.LoginData {
        smsVerificationService.verify(command.phone, command.code, SmsVerificationService.Purpose.LOGIN)
        val user = userRepository.findByPhone(command.phone.trim())
            ?: throw BadCredentialsException("手机号或验证码错误")
        if (!user.enabled || user.status != User.Status.Activity) throw BadCredentialsException("手机号或验证码错误")
        val role = SecurityRole.normalizeOrNull(user.role) ?: throw BadCredentialsException("手机号或验证码错误")
        return issueToken(user, role)
    }

    /**
     * resetPassword：更新业务状态或修改相关配置。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param command 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    override fun resetPassword(command: AuthService.ResetPasswordCommand) {
        smsVerificationService.verify(command.phone, command.code, SmsVerificationService.Purpose.RESET_PASSWORD)
        require(command.newPassword.isNotBlank()) { "新密码不能为空" }
        val user =
            userRepository.findByPhone(command.phone.trim()) ?: throw BadCredentialsException("手机号或验证码错误")
        user.passwordHash = passwordEncoder.encode(command.newPassword).toString()
        // 用户已自行设定新密码，初始密码不再有效，「必须改密」随之解除。
        user.mustChangePassword = false
        user.updatedAt = LocalDateTime.now()
        userRepository.save(user)
        user.id?.let(sessionRepository::incrementTokenVersion)
    }

    /**
     * issueToken：校验输入、状态或访问条件。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param user 参与本次处理的输入参数。
     * @param role 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    private fun issueToken(user: User, role: String): AuthService.LoginData {
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
                workingDepartmentId = defaultWorkingDepartment(user, role),
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
            defaultWorkingDepartment(user, role),
        )
    }

    /**
     * 登录时的默认工作部门。
     *
     * 部门管理必须落在自己的归属部门——它的数据范围始终受部门限制，默认「全部」会立刻
     * 得到一个看不到任何数据的会话。其余角色默认为「全部」，与引入工作部门之前的行为一致，
     * 升级后存量用户不会突然失去可见数据。
     */
    private fun defaultWorkingDepartment(user: User, role: String): Long? =
        if (role == SecurityRole.DEPT_ADMIN) user.department?.id else null

    /**
     * logout：删除、清理或撤销相关数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param tokenId 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
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
        /** 会话正文结构版本；新增 must_change_password 字段后升到 2，旧会话缺字段时按 false 解析。 */
        const val SESSION_SCHEMA_VERSION = 3
    }
}
