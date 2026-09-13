package top.foxball.cartask.config

import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.EnvironmentPostProcessor
import org.springframework.boot.SpringApplication
import org.springframework.core.io.support.SpringFactoriesLoader
import org.springframework.mock.env.MockEnvironment
import top.foxball.cartask.CarTaskApplication

class DotenvEnvironmentPostProcessorTests {
    @Test
    fun `已在 spring factories 注册且可被 Spring 反射实例化`() {
        val names = SpringFactoriesLoader.loadFactoryNames(
            EnvironmentPostProcessor::class.java,
            javaClass.classLoader,
        )

        assertTrue(DotenvEnvironmentPostProcessor::class.java.name in names)
        assertTrue(DotenvEnvironmentPostProcessor::class.java.declaredConstructors.any { it.parameterCount == 0 })
    }

    @Test
    fun `启动时把 dotenv 文件注入 Environment`() {
        val directory = createTempDirectory("dotenv-post-processor")
        Files.writeString(
            directory.resolve(".env"),
            """
            # 本地运行配置
            DB_URL=jdbc:postgresql://127.0.0.1:5432/cartask
            KEYTOP_PARK_ID=591007282
            """.trimIndent(),
        )
        val environment = MockEnvironment()

        DotenvEnvironmentPostProcessor(directory.resolve(".env"))
            .postProcessEnvironment(environment, SpringApplication(CarTaskApplication::class.java))

        assertEquals("jdbc:postgresql://127.0.0.1:5432/cartask", environment.getProperty("DB_URL"))
        assertEquals("591007282", environment.getProperty("KEYTOP_PARK_ID"))
    }

    @Test
    fun `test profile 下不注入 dotenv 文件`() {
        val directory = createTempDirectory("dotenv-post-processor-test-profile")
        Files.writeString(directory.resolve(".env"), "DB_URL=jdbc:postgresql://127.0.0.1:5432/cartask")
        val environment = MockEnvironment()
        environment.setActiveProfiles("test")

        DotenvEnvironmentPostProcessor(directory.resolve(".env"))
            .postProcessEnvironment(environment, SpringApplication(CarTaskApplication::class.java))

        assertNull(environment.getProperty("DB_URL"))
    }
}
