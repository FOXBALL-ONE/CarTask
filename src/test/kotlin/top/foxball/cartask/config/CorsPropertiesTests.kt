package top.foxball.cartask.config

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * CORS 白名单的配置约束。
 *
 * 「任意来源」与「允许凭据」不能同时成立：浏览器不允许 `Access-Control-Allow-Origin: *` 与凭据共存，
 * 而 Spring 在两者同时配置时会直接抛异常。这里的规则是**启动期**就拒绝，而不是等浏览器报错。
 */
class CorsPropertiesTests {
    @Test
    fun `允许凭据时全局通配会被拒绝`() {
        assertThrows(IllegalArgumentException::class.java) {
            CorsProperties(allowedOrigins = listOf("*"), allowCredentials = true).validate()
        }
        assertThrows(IllegalArgumentException::class.java) {
            CorsProperties(allowedOriginPatterns = listOf("*"), allowCredentials = true).validate()
        }
    }

    @Test
    fun `关掉凭据后可以放行任意来源`() {
        // 临时联调用的组合：前端把 JWT 放在 Authorization 头里（credentials: omit），不需要浏览器凭据。
        assertDoesNotThrow {
            CorsProperties(allowedOriginPatterns = listOf("*"), allowCredentials = false).validate()
        }
    }

    @Test
    fun `白名单里的具体来源始终允许`() {
        assertDoesNotThrow {
            CorsProperties(
                allowedOrigins = listOf("http://localhost:8090", "http://127.0.0.1:8090"),
                allowCredentials = true,
            ).validate()
        }
    }

    @Test
    fun `空白项会被裁掉`() {
        val properties = CorsProperties(
            allowedOrigins = listOf("  ", " http://localhost:8090 ", "http://localhost:8090"),
            allowedOriginPatterns = listOf("", "  "),
            allowCredentials = true,
        )

        assertDoesNotThrow { properties.validate() }
        assertEquals(listOf("http://localhost:8090"), properties.origins())
        assertEquals(emptyList<String>(), properties.originPatterns())
    }
}
