package top.foxball.cartask.service

import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment
import top.foxball.cartask.config.DotenvLoader
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SystemConfigServiceTests {
    private var previousEnvFile: String? = null

    @AfterTest
    fun restoreEnvFileVariable() {
        val previous = previousEnvFile
        if (previous == null) {
            System.clearProperty(DotenvLoader.ENV_FILE_VARIABLE)
        } else {
            System.setProperty(DotenvLoader.ENV_FILE_VARIABLE, previous)
        }
    }


    /**
     * 让 .env 指向临时文件，properties 用来模拟 application.yaml 里 cartask.* 占位符的解析结果
     * ——单测里不会加载 yaml，所以「当前生效值」只能这样喂进来。
     */
    private fun service(envContent: String = "", properties: Map<String, Any> = emptyMap()): SystemConfigService {
        previousEnvFile = System.getProperty(DotenvLoader.ENV_FILE_VARIABLE)
        val path = createTempDirectory("system-config").resolve(".env")
        Files.writeString(path, envContent)
        System.setProperty(DotenvLoader.ENV_FILE_VARIABLE, path.toString())
        val environment = StandardEnvironment()
        if (properties.isNotEmpty()) {
            environment.propertySources.addFirst(MapPropertySource("test", properties))
        }
        return SystemConfigService(environment)
    }


    private fun envValues(): Map<String, String> = DotenvLoader.read(DotenvLoader.defaultPath())


    @Test
    fun `保存只改动目标项并保留注释与其它配置`() {
        val service = service(
            """
            # 这条注释必须留着
            DB_URL=jdbc:postgresql://localhost:5432/cartask
            SMS_ENABLED=false
            SMS_SIGN_NAME=旧签名
            """.trimIndent() + "\n",
        )

        service.write(mapOf("SMS_SIGN_NAME" to "新签名"))

        val text = Files.readString(DotenvLoader.defaultPath())
        assertTrue(text.contains("# 这条注释必须留着"))
        assertTrue(text.contains("DB_URL=jdbc:postgresql://localhost:5432/cartask"))
        assertTrue(text.contains("SMS_ENABLED=false"))
        assertEquals("新签名", envValues()["SMS_SIGN_NAME"])
        assertEquals(1, text.lines().count { it.startsWith("SMS_SIGN_NAME=") })
    }


    @Test
    fun `文件里没有的配置项追加到末尾`() {
        val service = service("DB_URL=x\n")

        service.write(mapOf("FILE_BASE_URL" to "http://example.com"))

        assertEquals("http://example.com", envValues()["FILE_BASE_URL"])
        assertEquals("x", envValues()["DB_URL"])
    }


    @Test
    fun `白名单之外的键不会被写入`() {
        val service = service("DB_URL=x\n")

        service.write(mapOf("DB_URL" to "被改掉了", "SMS_ENABLED" to "true"))

        assertEquals("x", envValues()["DB_URL"])
        assertEquals("true", envValues()["SMS_ENABLED"])
    }


    @Test
    fun `密钥留空时不覆盖已有值`() {
        val service = service("SMS_ACCESS_KEY_SECRET=原密钥\nSMS_ENABLED=false\n")

        service.write(mapOf("SMS_ACCESS_KEY_SECRET" to "", "SMS_ENABLED" to "true"))

        assertEquals("原密钥", envValues()["SMS_ACCESS_KEY_SECRET"])
        assertEquals("true", envValues()["SMS_ENABLED"])
    }


    @Test
    fun `读取时密钥以掩码返回而不回显明文`() {
        val service = service(properties = mapOf("cartask.sms.access-key-secret" to "super-secret"))

        assertEquals("******", service.read()["SMS_ACCESS_KEY_SECRET"])
    }


    @Test
    fun `允许凭据时拒绝把跨域来源配成通配`() {
        val service = service(
            properties = mapOf(
                "cartask.security.cors.allowed-origins" to "http://a.com",
                "cartask.security.cors.allow-credentials" to "true",
            ),
        )

        val failure = assertFailsWith<IllegalArgumentException> {
            service.write(mapOf("CORS_ALLOWED_ORIGINS" to "*", "CORS_ALLOW_CREDENTIALS" to "true"))
        }

        assertTrue(failure.message.orEmpty().contains("*"))
    }


    @Test
    fun `含井号的值加引号后仍能原样读回`() {
        val service = service("DB_URL=x\n")

        service.write(mapOf("SMS_SIGN_NAME" to "车场 #1"))

        assertEquals("车场 #1", envValues()["SMS_SIGN_NAME"])
    }
}
