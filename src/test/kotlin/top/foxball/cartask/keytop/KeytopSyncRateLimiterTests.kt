package top.foxball.cartask.keytop

import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment
import top.foxball.cartask.config.DotenvLoader
import java.time.Duration

class KeytopSyncRateLimiterTests {
    private var previousEnvFile: String? = null

    @AfterTest
    fun restoreEnvFileVariable() {
        if (previousEnvFile == null) System.clearProperty(DotenvLoader.ENV_FILE_VARIABLE)
        else System.setProperty(DotenvLoader.ENV_FILE_VARIABLE, previousEnvFile)
    }

    @Test
    fun `同步开始后配置变更不会影响已有快照`() {
        previousEnvFile = System.getProperty(DotenvLoader.ENV_FILE_VARIABLE)
        val path = createTempDirectory("keytop-rate-limit").resolve(".env")
        Files.writeString(path, "KEYTOP_SYNC_REQUEST_INTERVAL=200ms\n")
        System.setProperty(DotenvLoader.ENV_FILE_VARIABLE, path.toString())

        val environment = StandardEnvironment().apply {
            propertySources.addFirst(MapPropertySource("test", mapOf("keytop.sync-request-interval" to "1s")))
        }
        val limiter = KeytopSyncRateLimiter(environment)
        val runningSnapshot = limiter.snapshot()
        Files.writeString(path, "KEYTOP_SYNC_REQUEST_INTERVAL=1s\n")

        assertEquals(Duration.ofMillis(200), runningSnapshot.interval)
        assertEquals(Duration.ofSeconds(1), limiter.snapshot().interval)
    }

    @Test
    fun `嵌套调用沿用外层任务快照`() {
        previousEnvFile = System.getProperty(DotenvLoader.ENV_FILE_VARIABLE)
        val path = createTempDirectory("keytop-rate-limit").resolve(".env")
        Files.writeString(path, "")
        System.setProperty(DotenvLoader.ENV_FILE_VARIABLE, path.toString())
        val environment = StandardEnvironment().apply {
            propertySources.addFirst(MapPropertySource("test", mapOf("keytop.sync-request-interval" to "1s")))
        }
        val limiter = KeytopSyncRateLimiter(environment)
        val outer = KeytopSyncRateLimiter.Snapshot(Duration.ofMillis(10))

        limiter.withSnapshot(outer) {
            assertEquals(outer, limiter.snapshot())
        }
        assertEquals(Duration.ofSeconds(1), limiter.snapshot().interval)
    }
}
