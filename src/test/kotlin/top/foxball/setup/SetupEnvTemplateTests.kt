package top.foxball.setup

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import top.foxball.cartask.config.DotenvLoader

class SetupEnvTemplateTests {
    private val generatedAt = LocalDateTime.of(2026, 9, 13, 22, 41, 7)

    @Test
    fun `写出的内容能被 dotenv 解析器原样读回`() {
        val text = SetupEnvTemplate.render(
            values = mapOf(
                "DB_URL" to "jdbc:postgresql://192.168.1.95:5432/cartask",
                "DB_USERNAME" to "cartask",
                "DB_PASSWORD" to "p@ss word",
                "REDIS_HOST" to "192.168.1.95",
                "REDIS_PORT" to "6379",
            ),
            existing = emptyMap(),
            frontendOrigin = null,
            generatedAt = generatedAt,
        )

        val parsed = parse(text)
        assertEquals("jdbc:postgresql://192.168.1.95:5432/cartask", parsed["DB_URL"])
        assertEquals("cartask", parsed["DB_USERNAME"])
        assertEquals("p@ss word", parsed["DB_PASSWORD"])
        assertEquals("6379", parsed["REDIS_PORT"])
    }

    @Test
    fun `引导没问的键按默认值补齐`() {
        val parsed = parse(render(emptyMap()))

        assertEquals("update", parsed["JPA_DDL_AUTO"])
        assertEquals("2s", parsed["REDIS_TIMEOUT"])
        assertEquals("false", parsed["SMS_ENABLED"])
        assertEquals("https://kp-open.keytop.cn/unite-api", parsed["KEYTOP_BASE_URL"])
        assertEquals("carTask", parsed["JWT_ISSUER"])
    }

    @Test
    fun `JWT 密钥沿用既有配置，重新引导不会踢掉在线会话`() {
        val existing = mapOf(
            "JWT_SIGNING_KEY_LOCAL" to "old-signing-key",
            "JWT_STORAGE_ENCRYPTION_KEY" to "old-storage-key",
        )
        val parsed = parse(render(emptyMap(), existing))

        assertEquals("old-signing-key", parsed["JWT_SIGNING_KEY_LOCAL"])
        assertEquals("old-storage-key", parsed["JWT_STORAGE_ENCRYPTION_KEY"])
    }

    @Test
    fun `没有既有密钥时生成两把不同的 32 字节 Base64 密钥`() {
        val parsed = parse(render(emptyMap()))

        val signing = parsed.getValue("JWT_SIGNING_KEY_LOCAL")
        val storage = parsed.getValue("JWT_STORAGE_ENCRYPTION_KEY")
        assertNotEquals(signing, storage)
        assertEquals(32, java.util.Base64.getDecoder().decode(signing).size)
        assertEquals(32, java.util.Base64.getDecoder().decode(storage).size)
    }

    @Test
    fun `提交引导页的 Origin 会并入跨域白名单`() {
        val parsed = parse(render(emptyMap(), emptyMap(), "http://192.168.1.95:8090"))

        assertEquals(
            "http://localhost:8090,http://127.0.0.1:8090,http://192.168.1.95:8090",
            parsed["CORS_ALLOWED_ORIGINS"],
        )
    }

    @Test
    fun `已有跨域白名单时在其基础上追加，不覆盖`() {
        val parsed = parse(
            render(
                emptyMap(),
                mapOf("CORS_ALLOWED_ORIGINS" to "https://park.example.com"),
                "https://park.example.com",
            ),
        )

        assertEquals("https://park.example.com", parsed["CORS_ALLOWED_ORIGINS"])
    }

    @Test
    fun `含井号的值加引号，避免被读成注释`() {
        val text = render(mapOf("DB_PASSWORD" to "abc#def"))

        assertContains(text, """DB_PASSWORD="abc#def"""")
        assertEquals("abc#def", parse(text)["DB_PASSWORD"])
    }

    @Test
    fun `含首尾空格的值加引号，避免被读取端 trim 掉`() {
        val text = render(mapOf("DB_PASSWORD" to " padded "))

        assertContains(text, """DB_PASSWORD=" padded """")
        assertEquals(" padded ", parse(text)["DB_PASSWORD"])
    }

    @Test
    fun `值里带换行直接拒绝，不让它把配置文件撑成两行`() {
        assertFailsWith<IllegalArgumentException> {
            render(mapOf("DB_PASSWORD" to "line1\nline2"))
        }
    }

    @Test
    fun `重新走引导的入口以注释形式写在文件头`() {
        val text = render(emptyMap())

        assertContains(text, "#  SETUP_MODE=true")
        assertTrue("SETUP_MODE" !in parse(text), "注释掉的开关不该被解析成一个生效的键")
    }

    private fun render(
        values: Map<String, String>,
        existing: Map<String, String> = emptyMap(),
        frontendOrigin: String? = null,
    ): String = SetupEnvTemplate.render(values, existing, frontendOrigin, generatedAt)

    /** 用生产环境同一套解析器读回，避免测试自带的解析规则与 DotenvLoader 悄悄跑偏。 */
    private fun parse(text: String): Map<String, String> {
        val file = kotlin.io.path.createTempFile("setup-env", ".env")
        java.nio.file.Files.writeString(file, text)
        return DotenvLoader.read(file)
    }
}
