package top.foxball.cartask.config

import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.StandardEnvironment
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DotenvLoaderTests {
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

    @Test
    fun `dotenv values are added after system environment`() {
        val directory = createTempDirectory("dotenv-loader")
        Files.writeString(
            directory.resolve(".env"),
            """
            # local settings
            JWT_SIGNING_KEY_LOCAL="signing-key"
            export FILE_BASE_URL=http://localhost:8080
            EMPTY=
            """.trimIndent(),
        )
        val environment = StandardEnvironment()
        DotenvLoader.addTo(environment, directory.resolve(".env"))

        assertEquals("signing-key", environment.getProperty("JWT_SIGNING_KEY_LOCAL"))
        assertEquals("http://localhost:8080", environment.getProperty("FILE_BASE_URL"))
        assertEquals("", environment.getProperty("EMPTY"))
        assertNull(environment.getProperty("MISSING"))
    }

    @Test
    fun `未指定时配置文件是工作目录下的 dotenv`() {
        assertEquals(
            Path.of(".env").toAbsolutePath().normalize(),
            DotenvLoader.defaultPath(),
        )
    }

    @Test
    fun `APP_ENV_FILE 能把配置文件指到别处`() {
        previousEnvFile = System.getProperty(DotenvLoader.ENV_FILE_VARIABLE)
        val target = createTempDirectory("dotenv-elsewhere").resolve("production.env")
        System.setProperty(DotenvLoader.ENV_FILE_VARIABLE, target.toString())

        // 启动模式判定、Spring 注入、引导写入、口令擦除都取这一个值；不一致就会出现
        // 「引导写完了但启动时读不到」。
        assertEquals(target.toAbsolutePath().normalize(), DotenvLoader.defaultPath())
    }
}
