package top.foxball.cartask.config

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test







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
