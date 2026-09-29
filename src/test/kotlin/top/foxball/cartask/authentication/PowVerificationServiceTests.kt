package top.foxball.cartask.authentication

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.security.authentication.BadCredentialsException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class PowVerificationServiceTests {
    private val redis = mock<StringRedisTemplate>()
    private val clock = Clock.fixed(Instant.parse("2026-09-29T00:00:00Z"), ZoneOffset.UTC)
    private val service = PowVerificationService(redis, clock)

    @Test
    fun `签发挑战并拒绝非法证明`() {
        whenever(redis.opsForValue().setIfAbsent(any(), any(), any<java.time.Duration>())).thenReturn(true)
        val challenge = service.issue("LOGIN")
        assertThrows(BadCredentialsException::class.java) {
            service.verify(challenge.challengeId, "SMS_SEND", "0")
        }
    }

    @Test
    fun `Redis挑战消费返回非成功时拒绝`() {
        whenever(redis.opsForValue().get(any())).thenReturn("LOGIN|seed|16|4102444800000|v1")
        whenever(redis.execute<Long>(any<RedisScript<Long>>(), any<List<String>>(), any<Any>())).thenReturn(0L)
        assertThrows(BadCredentialsException::class.java) {
            service.verify("challenge", "LOGIN", "0")
        }
    }

    @Test
    fun `按位检查摘要前缀的零位`() {
        assertTrue(service.hasLeadingZeroBitsForTest(byteArrayOf(0, 0x0f), 12))
        assertFalse(service.hasLeadingZeroBitsForTest(byteArrayOf(0, 0x1f), 12))
        assertTrue(service.hasLeadingZeroBitsForTest(byteArrayOf(0, 0), 16))
    }
}
