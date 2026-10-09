package top.foxball.cartask.authentication

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import top.foxball.cartask.handler.EmailSendFailedException
import top.foxball.cartask.handler.VerificationCodeRateLimitException
import top.foxball.cartask.sms.SmsClient
import top.foxball.cartask.sms.SmsProperties

class SmsVerificationRateLimitTests {
    private val redisTemplate = mock<StringRedisTemplate>()
    private val valueOperations = mock<ValueOperations<String, String>>()
    private val smsClient = mock<SmsClient>()
    private val service = SmsVerificationService(redisTemplate, smsClient, SmsProperties())
    private val phone = "13800138000"
    private val cooldownKey = "shopmall:auth:sms:cooldown:login:$phone"
    private val codeKey = "shopmall:auth:sms:login:$phone"

    init {
        whenever(redisTemplate.opsForValue()).thenReturn(valueOperations)
    }

    @Test
    fun `only the request that acquires cooldown can send`() {
        whenever(valueOperations.setIfAbsent(cooldownKey, "1", SmsVerificationService.SEND_INTERVAL))
            .thenReturn(true, false)

        service.send(phone, SmsVerificationService.Purpose.LOGIN)
        assertThrows(VerificationCodeRateLimitException::class.java) {
            service.send(phone, SmsVerificationService.Purpose.LOGIN)
        }

        verify(valueOperations).set(eq(codeKey), any<String>(), eq(SmsVerificationService.CODE_TTL))
        verify(smsClient).sendVerificationCode(eq(phone), any())
        verify(redisTemplate, never()).hasKey(any())
    }

    @Test
    fun `missing acquisition result does not send or write code`() {
        whenever(valueOperations.setIfAbsent(cooldownKey, "1", SmsVerificationService.SEND_INTERVAL))
            .thenReturn(null)

        assertThrows(VerificationCodeRateLimitException::class.java) {
            service.send(phone, SmsVerificationService.Purpose.LOGIN)
        }

        verify(valueOperations, never()).set(any<String>(), any<String>(), any<java.time.Duration>())
        verify(smsClient, never()).sendVerificationCode(any(), any())
    }

    @Test
    fun `code write failure releases acquired cooldown`() {
        whenever(valueOperations.setIfAbsent(cooldownKey, "1", SmsVerificationService.SEND_INTERVAL))
            .thenReturn(true)
        whenever(valueOperations.set(eq(codeKey), any<String>(), eq(SmsVerificationService.CODE_TTL)))
            .thenThrow(org.springframework.dao.DataAccessResourceFailureException("Redis unavailable"))

        assertThrows(EmailSendFailedException::class.java) {
            service.send(phone, SmsVerificationService.Purpose.LOGIN)
        }

        verify(redisTemplate).delete(cooldownKey)
        verify(smsClient, never()).sendVerificationCode(any(), any())
    }
}
