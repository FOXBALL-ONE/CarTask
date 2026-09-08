package top.foxball.cartask.logging

import java.time.LocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations

class LogVisibilityServiceTests {
    private val redisTemplate = mock<StringRedisTemplate>()
    private val values = mock<ValueOperations<String, String>>()
    private val service = LogVisibilityService(redisTemplate)

    @Test
    fun `读取 ISO 时间作为登录日志可见时间`() {
        whenever(redisTemplate.opsForValue()).thenReturn(values)
        whenever(values.get("shopmall:logs:visible-after:login")).thenReturn("2026-09-08T20:00:00")

        assertEquals(LocalDateTime.of(2026, 9, 8, 20, 0), service.visibleAfter(LogVisibilityService.Type.LOGIN))
    }

    @Test
    fun `清空操作日志时写入 ISO 时间`() {
        whenever(redisTemplate.opsForValue()).thenReturn(values)
        val value = argumentCaptor<String>()

        service.clear(LogVisibilityService.Type.OPERATION)

        verify(values).set(eq("shopmall:logs:visible-after:operation"), value.capture())
        assertNotNull(LocalDateTime.parse(value.firstValue))
    }
}
