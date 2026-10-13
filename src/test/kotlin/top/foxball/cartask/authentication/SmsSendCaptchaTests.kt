package top.foxball.cartask.authentication

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.crypto.password.PasswordEncoder
import top.foxball.cartask.repository.UserRepository





class SmsSendCaptchaTests {
    private val captchaService = mock<CaptchaService>()
    private val smsVerificationService = mock<SmsVerificationService>()
    private val service = AuthServiceImpl(
        userRepository = mock<UserRepository>(),
        passwordEncoder = mock<PasswordEncoder>(),
        jwtTokenService = mock<JwtTokenService>(),
        sessionRepository = mock<RedisTokenSessionRepository>(),
        captchaService = captchaService,
        loginAttemptLimiter = mock<LoginAttemptLimiter>(),
        rolePermissionService = mock<RolePermissionService>(),
        smsVerificationService = smsVerificationService,
    )

    @Test
    fun `图形验证码校验通过后发送短信`() {
        service.sendSmsCode(
            AuthService.SmsSendCommand(
                phone = "13800138000",
                purpose = "LOGIN",
                captchaToken = "token-1",
                captchaAnswer = "1234",
            ),
        )

        verify(captchaService).verify("token-1", "1234")
        verify(smsVerificationService).send("13800138000", SmsVerificationService.Purpose.LOGIN)
    }

    @Test
    fun `图形验证码错误时不发送短信`() {
        whenever(captchaService.verify(any(), any())).thenThrow(BadCredentialsException("验证码错误"))

        assertThrows(BadCredentialsException::class.java) {
            service.sendSmsCode(
                AuthService.SmsSendCommand("13800138000", "LOGIN", "token-1", "0000"),
            )
        }

        verify(smsVerificationService, never()).send(any(), any())
    }

    @Test
    fun `用途非法时不消耗图形验证码`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.sendSmsCode(
                AuthService.SmsSendCommand("13800138000", "UNKNOWN", "token-1", "1234"),
            )
        }

        verify(captchaService, never()).verify(any(), any())
        verify(smsVerificationService, never()).send(any(), any())
    }

    @Test
    fun `短信验证被临时关闭时连图形验证码一起跳过`() {
        whenever(smsVerificationService.verificationSkipped).thenReturn(true)

        service.sendSmsCode(AuthService.SmsSendCommand("13800138000", "CHANGE_PHONE", null, null))

        verify(captchaService, never()).verify(any(), any())
        verify(smsVerificationService).send("13800138000", SmsVerificationService.Purpose.CHANGE_PHONE)
    }
}
