package top.foxball.cartask.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.crypto.password.PasswordEncoder
import top.foxball.cartask.audit.AuditAction
import top.foxball.cartask.audit.AuditCommand
import top.foxball.cartask.audit.AuditService
import top.foxball.cartask.authentication.RedisTokenSessionRepository
import top.foxball.cartask.authentication.SmsVerificationService
import top.foxball.cartask.entity.User
import top.foxball.cartask.handler.VerificationCodeInvalidException
import top.foxball.cartask.repository.UserRepository
import top.foxball.cartask.service.impl.ProfileServiceImpl
import java.time.LocalDateTime
import java.util.Base64


class ProfileServiceTests {
    private val userRepository = mock<UserRepository>()
    private val passwordEncoder = mock<PasswordEncoder>()
    private val tokenSessionRepository = mock<RedisTokenSessionRepository>()
    private val smsVerificationService = mock<SmsVerificationService>()
    private val auditService = mock<AuditService>()
    private val service = ProfileServiceImpl(userRepository, passwordEncoder, tokenSessionRepository, smsVerificationService, auditService)

    private fun storedUser(mustChangePassword: Boolean = true): User = User().apply {
        id = 7L
        username = "zhangsan"
        email = "zhangsan@local.invalid"
        passwordHash = "encoded-old"
        this.mustChangePassword = mustChangePassword
        createdAt = LocalDateTime.of(2026, 1, 1, 0, 0)
        updatedAt = LocalDateTime.of(2026, 1, 1, 0, 0)
    }

    private fun stubUser(user: User) {
        whenever(userRepository.findById(7L)).thenReturn(java.util.Optional.of(user))
        whenever(userRepository.save(any<User>())).thenAnswer { it.getArgument(0) }
    }

    @Test
    fun `原密码错误时拒绝改密且不撤销会话`() {
        val user = storedUser()
        stubUser(user)
        whenever(passwordEncoder.matches("wrong", "encoded-old")).thenReturn(false)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.changePassword(7L, ProfileService.ChangePasswordCommand("wrong", "new-password"))
        }

        assertEquals("原密码不正确", ex.message)
        assertEquals("encoded-old", user.passwordHash)
        assertTrue(user.mustChangePassword)
        verify(userRepository, never()).save(any<User>())
        verify(tokenSessionRepository, never()).incrementTokenVersion(any())
    }

    @Test
    fun `改密成功会清除必须改密标记并撤销全部历史会话`() {
        val user = storedUser()
        stubUser(user)
        whenever(passwordEncoder.matches("old-password", "encoded-old")).thenReturn(true)
        whenever(passwordEncoder.matches("new-password", "encoded-old")).thenReturn(false)
        whenever(passwordEncoder.encode("new-password")).thenReturn("encoded-new")

        service.changePassword(7L, ProfileService.ChangePasswordCommand("old-password", "new-password"))

        assertEquals("encoded-new", user.passwordHash)
        assertFalse(user.mustChangePassword)
        verify(tokenSessionRepository).incrementTokenVersion(7L)
    }

    @Test
    fun `新密码过短或与原密码相同都会被拒绝`() {
        val user = storedUser()
        stubUser(user)
        whenever(passwordEncoder.matches("old-password", "encoded-old")).thenReturn(true)
        whenever(passwordEncoder.matches("short", "encoded-old")).thenReturn(false)
        whenever(passwordEncoder.matches("old-password", "encoded-old")).thenReturn(true)

        assertThrows(IllegalArgumentException::class.java) {
            service.changePassword(7L, ProfileService.ChangePasswordCommand("old-password", "short"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.changePassword(7L, ProfileService.ChangePasswordCommand("old-password", "old-password"))
        }

        assertEquals("encoded-old", user.passwordHash)
        verify(tokenSessionRepository, never()).incrementTokenVersion(any())
    }

    @Test
    fun `头像必须是内容与声明一致的图片`() {
        val user = storedUser()
        stubUser(user)
        val htmlPayload = Base64.getEncoder().encodeToString("<svg onload=alert(1)>".toByteArray())

        assertThrows(IllegalArgumentException::class.java) {
            service.updateAvatar(7L, ProfileService.AvatarCommand("data:image/png;base64,$htmlPayload"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.updateAvatar(7L, ProfileService.AvatarCommand("data:text/html;base64,$htmlPayload"))
        }

        assertNull(user.avatar)
    }

    @Test
    fun `合法 PNG 头像会被保存并可清除`() {
        val user = storedUser()
        stubUser(user)
        val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D, 0x0A, 0x1A, 0x0A, 0x00)
        val dataUrl = "data:image/png;base64,${Base64.getEncoder().encodeToString(png)}"

        val updated = service.updateAvatar(7L, ProfileService.AvatarCommand(dataUrl))

        assertEquals(dataUrl, user.avatar)
        assertEquals(dataUrl, updated.avatar)

        val cleared = service.updateAvatar(7L, ProfileService.AvatarCommand("  "))

        assertNull(user.avatar)
        assertNull(cleared.avatar)
    }

    @Test
    fun `短信验证码不正确时换绑手机号被拒绝`() {
        val user = storedUser()
        user.phone = "13800138000"
        stubUser(user)
        whenever(smsVerificationService.verify(any(), any(), any())).thenThrow(VerificationCodeInvalidException())

        assertThrows(VerificationCodeInvalidException::class.java) {
            service.changePhone(7L, ProfileService.ChangePhoneCommand("13900139000", "000000"))
        }

        assertEquals("13800138000", user.phone)
        verify(userRepository, never()).save(any<User>())
    }

    @Test
    fun `手机号格式非法时连验证码都不校验`() {
        val user = storedUser()
        stubUser(user)

        assertThrows(IllegalArgumentException::class.java) {
            service.changePhone(7L, ProfileService.ChangePhoneCommand("not-a-phone", "123456"))
        }

        verify(smsVerificationService, never()).verify(any(), any(), any())
    }

    @Test
    fun `换绑到其他账号已绑定的手机号会被拒绝`() {
        val user = storedUser()
        user.phone = "13800138000"
        stubUser(user)
        whenever(userRepository.existsByPhoneAndIdNot("13900139000", 7L)).thenReturn(true)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.changePhone(7L, ProfileService.ChangePhoneCommand("13900139000", "123456"))
        }

        assertEquals("该手机号已被其他账号绑定", ex.message)
        assertEquals("13800138000", user.phone)
        verify(userRepository, never()).save(any<User>())
    }

    @Test
    fun `验证码通过后换绑手机号并记录审计`() {
        val user = storedUser()
        user.phone = "13800138000"
        stubUser(user)

        val updated = service.changePhone(7L, ProfileService.ChangePhoneCommand(" 13900139000 ", "123456"))

        verify(smsVerificationService).verify("13900139000", "123456", SmsVerificationService.Purpose.CHANGE_PHONE)
        assertEquals("13900139000", user.phone)
        assertEquals("13900139000", updated.phone)
        verify(auditService).record(
            org.mockito.kotlin.check<AuditCommand> { assertEquals(AuditAction.AUTH_PHONE_CHANGED, it.action) },
        )
    }
}
