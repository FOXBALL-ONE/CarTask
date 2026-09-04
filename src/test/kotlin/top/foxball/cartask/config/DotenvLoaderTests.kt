package top.foxball.cartask.config

import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.StandardEnvironment
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DotenvLoaderTests {
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
}
