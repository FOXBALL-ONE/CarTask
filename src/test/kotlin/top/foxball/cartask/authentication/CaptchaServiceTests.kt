package top.foxball.cartask.authentication

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.security.authentication.BadCredentialsException

class CaptchaServiceTests {
    private val redisTemplate = mock<StringRedisTemplate>()
    private val service = CaptchaService(redisTemplate)

    @Test
    fun `生成验证码返回token和图片`() {
        whenever(
            redisTemplate.execute<Long>(
                any<RedisScript<Long>>(),
                any<List<String>>(),
                any<Any>(),
                any<Any>(),
                any<Any>(),
                any<Any>(),
                any<Any>(),
            ),
        ).thenReturn(1L)

        val captcha = service.generate()

        assertTrue(captcha.image.startsWith("data:image/svg+xml;base64,"))
        assertTrue(captcha.token.isNotBlank())
    }

    @Test
    fun `Redis脚本返回成功时验证码通过`() {
        whenever(
            redisTemplate.execute<Long>(
                any<RedisScript<Long>>(),
                any<List<String>>(),
                any<Any>(),
                any<Any>(),
            ),
        ).thenReturn(1L)

        assertDoesNotThrow { service.verify("captcha-token", "1234") }
    }

    @Test
    fun `Redis脚本返回不同结果时映射为对应验证码错误`() {
        whenever(
            redisTemplate.execute<Long>(
                any<RedisScript<Long>>(),
                any<List<String>>(),
                any<Any>(),
                any<Any>(),
            ),
        ).thenReturn(-1L)
        assertThrows(BadCredentialsException::class.java) {
            service.verify("captcha-token", "1234")
        }

        whenever(
            redisTemplate.execute<Long>(
                any<RedisScript<Long>>(),
                any<List<String>>(),
                any<Any>(),
                any<Any>(),
            ),
        ).thenReturn(0L)
        assertThrows(BadCredentialsException::class.java) {
            service.verify("captcha-token", "1234")
        }
    }

    @Test
    fun `Redis异常转换为认证基础设施异常`() {
        whenever(
            redisTemplate.execute<Long>(
                any<RedisScript<Long>>(),
                any<List<String>>(),
                any<Any>(),
                any<Any>(),
            ),
        )
            .thenThrow(IllegalStateException("redis unavailable"))

        assertThrows(AuthenticationInfrastructureException::class.java) {
            service.verify("captcha-token", "1234")
        }
    }
}
