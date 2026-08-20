package top.foxball.cartask.logging

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LogSanitizerTests {
    @Test
    fun `敏感键和Bearer令牌不会进入日志消息`() {
        val result = LogSanitizer.sanitize(
            "password=secret token=jwt-value Authorization=Bearer abc.def phone=13812345678 plateNo=粤A12345",
        )

        assertFalse("secret" in result)
        assertFalse("jwt-value" in result)
        assertFalse("abc.def" in result)
        assertTrue(result.contains("[REDACTED]"))
    }

    @Test
    fun `未知普通消息保持可读`() {
        assertTrue(LogSanitizer.sanitize("同步完成 12 条") == "同步完成 12 条")
    }

    @Test
    fun `凭据出现在带空格的值中也会脱敏`() {
        val result = LogSanitizer.sanitize("Authorization: Bearer abc.def.ghi; password=top-secret")
        assertFalse("abc.def.ghi" in result)
        assertFalse("top-secret" in result)
    }
}
