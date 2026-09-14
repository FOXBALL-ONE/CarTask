package top.foxball.setup

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SetupModeTests {
    @Test
    fun `没有 DB_URL 时进入配置引导模式`() {
        assertTrue(SetupMode.required(mapOf("REDIS_HOST" to "localhost")))
    }

    @Test
    fun `配置过 DB_URL 就正常运行`() {
        assertFalse(SetupMode.required(mapOf("DB_URL" to "jdbc:postgresql://localhost:5432/cartask")))
    }

    @Test
    fun `DB_URL 是空串等同于没配置`() {
        assertTrue(SetupMode.required(mapOf("DB_URL" to "   ")))
    }

    @Test
    fun `SETUP_MODE 为真时即使已配置也重新引导`() {
        assertTrue(
            SetupMode.required(
                mapOf(
                    "SETUP_MODE" to "true",
                    "DB_URL" to "jdbc:postgresql://localhost:5432/cartask",
                ),
            ),
        )
    }

    @Test
    fun `SETUP_MODE 为假时即使没配置也按正常运行`() {
        assertFalse(SetupMode.required(mapOf("SETUP_MODE" to "false")))
    }

    @Test
    fun `SETUP_MODE 接受常见写法且不区分大小写`() {
        assertTrue(SetupMode.required(mapOf("SETUP_MODE" to " ON ")))
        assertTrue(SetupMode.required(mapOf("SETUP_MODE" to "Yes")))
        assertFalse(SetupMode.required(mapOf("SETUP_MODE" to "0")))
        assertFalse(SetupMode.required(mapOf("SETUP_MODE" to " off ")))
    }

    @Test
    fun `SETUP_MODE 值无法识别时退回按配置判定`() {
        assertTrue(SetupMode.required(mapOf("SETUP_MODE" to "maybe")))
        assertFalse(
            SetupMode.required(
                mapOf(
                    "SETUP_MODE" to "maybe",
                    "DB_URL" to "jdbc:postgresql://localhost:5432/cartask",
                ),
            ),
        )
    }
}
