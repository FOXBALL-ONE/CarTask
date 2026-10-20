package top.foxball.cartask.authentication

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.crypto.password.PasswordEncoder
import top.foxball.cartask.entity.User
import top.foxball.cartask.repository.UserRepository
import java.time.LocalDateTime

class AuthPasswordPolicyTests {
    private val userRepository = mock<UserRepository>()
    private val passwordEncoder = mock<PasswordEncoder>()
    private val sessionRepository = mock<RedisTokenSessionRepository>()
    private val smsVerificationService = mock<SmsVerificationService>()
    private val service = AuthServiceImpl(
        userRepository = userRepository,
        passwordEncoder = passwordEncoder,
        jwtTokenService = mock<JwtTokenService>(),
        sessionRepository = sessionRepository,
        captchaService = mock<CaptchaService>(),
        loginAttemptLimiter = mock<LoginAttemptLimiter>(),
        rolePermissionService = mock<RolePermissionService>(),
        smsVerificationService = smsVerificationService,
    )

    @Test
    fun `不符合全局规则的重置密码不会消耗短信验证码`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            service.resetPassword(AuthService.ResetPasswordCommand("13800138000", "123456", "abcdef12"))
        }

        assertEquals("新密码需包含大写字母、小写字母、数字、特殊符号中的至少 3 类", error.message)
        verify(smsVerificationService, never()).verify(any(), any(), any())
        verify(userRepository, never()).findByPhone(any())
        verify(userRepository, never()).save(any<User>())
    }

    @Test
    fun `符合全局规则的短信重置密码会保存并撤销旧会话`() {
        val user = User().apply {
            id = 7L
            username = "user"
            phone = "13800138000"
            passwordHash = "old-hash"
            createdAt = LocalDateTime.of(2026, 1, 1, 0, 0)
            updatedAt = LocalDateTime.of(2026, 1, 1, 0, 0)
        }
        whenever(userRepository.findByPhone("13800138000")).thenReturn(user)
        whenever(passwordEncoder.encode("Valid-password1!")).thenReturn("new-hash")

        service.resetPassword(AuthService.ResetPasswordCommand("13800138000", "123456", "Valid-password1!"))

        assertEquals("new-hash", user.passwordHash)
        verify(smsVerificationService).verify("13800138000", "123456", SmsVerificationService.Purpose.RESET_PASSWORD)
        verify(userRepository).save(user)
        verify(sessionRepository).incrementTokenVersion(7L)
    }
}
