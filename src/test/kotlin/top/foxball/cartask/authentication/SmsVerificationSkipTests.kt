package top.foxball.cartask.authentication

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import top.foxball.cartask.handler.VerificationCodeInvalidException
import top.foxball.cartask.sms.SmsClient
import top.foxball.cartask.sms.SmsProperties







class SmsVerificationSkipTests {
    private val redisTemplate = mock<StringRedisTemplate>()
    private val valueOperations = mock<ValueOperations<String, String>>()
    private val smsClient = mock<SmsClient>()

    private fun service(skipVerification: Boolean): SmsVerificationService {
        whenever(redisTemplate.opsForValue()).thenReturn(valueOperations)
        return SmsVerificationService(redisTemplate, smsClient, SmsProperties(skipVerification = skipVerification))
    }

    @Test
    fun `开关打开时不发短信也不写验证码`() {
        val service = service(skipVerification = true)

        service.send("13800138000", SmsVerificationService.Purpose.CHANGE_PHONE)

        verify(smsClient, never()).sendVerificationCode(any(), any())
        verify(redisTemplate, never()).opsForValue()
    }

    @Test
    fun `开关打开时任意验证码都通过且不读缓存`() {
        val service = service(skipVerification = true)

        assertDoesNotThrow {
            service.verify("13800138000", "", SmsVerificationService.Purpose.CHANGE_PHONE)
        }
        assertDoesNotThrow {
            service.verify("13800138000", "000000", SmsVerificationService.Purpose.LOGIN)
        }

        verify(redisTemplate, never()).opsForValue()
    }

    @Test
    fun `开关关闭时恢复原有校验`() {
        val service = service(skipVerification = false)
        whenever(valueOperations.getAndDelete(any<String>())).thenReturn(null)

        assertThrows(VerificationCodeInvalidException::class.java) {
            service.verify("13800138000", "123456", SmsVerificationService.Purpose.LOGIN)
        }
    }

    @Test
    fun `开关关闭时手机号格式非法仍然拒绝`() {
        val service = service(skipVerification = false)

        assertThrows(IllegalArgumentException::class.java) {
            service.verify("not-a-phone", "123456", SmsVerificationService.Purpose.LOGIN)
        }
    }
}
