package top.foxball.cartask.logging

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals

class LoggingPropertiesTests {
    @Test
    fun `有效大小配置可以绑定`() {
        val properties = LoggingProperties(
            directory = createTempDirectory("logging-properties").toString(),
            maxFileSize = "256MB",
            totalSizeCap = "1GB",
        )
        assertEquals(30, properties.retentionDays)
    }

    @Test
    fun `总容量不能小于单文件大小`() {
        assertFailsWith<IllegalArgumentException> {
            LoggingProperties(
                directory = createTempDirectory("logging-properties").toString(),
                maxFileSize = "2GB",
                totalSizeCap = "1GB",
            )
        }
    }
}
