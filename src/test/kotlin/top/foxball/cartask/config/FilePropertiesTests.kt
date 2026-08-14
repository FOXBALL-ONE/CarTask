package top.foxball.cartask.config

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FilePropertiesTests {
    @Test
    fun `normalizes a writable storage root and download base url`() {
        val root = createTempDirectory("file-properties-")

        val properties = FileProperties(
            storageRoot = root.resolve("files").toString(),
            baseUrl = "https://files.example.com/shopmall/",
        )

        assertEquals(root.resolve("files").toAbsolutePath().normalize(), properties.rootPath)
        assertTrue(Files.isDirectory(properties.rootPath))
        assertEquals("https://files.example.com/shopmall", properties.downloadBaseUrl)
    }

    @Test
    fun `rejects a missing or non-http download base url`() {
        val root = createTempDirectory("file-properties-")

        assertThrows<IllegalArgumentException> { FileProperties(root.toString(), "") }
        assertThrows<IllegalArgumentException> { FileProperties(root.toString(), "ftp://files.example.com") }
        assertThrows<IllegalArgumentException> { FileProperties(root.toString(), "https://files.example.com?key=value") }
    }
}
