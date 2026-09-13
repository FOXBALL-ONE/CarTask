package top.foxball.cartask.controller

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.core.io.Resource
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import top.foxball.cartask.service.DataBackupService
import top.foxball.cartask.shared.ResponseBuilder
import java.nio.file.Files
import java.nio.file.Path

class DataBackupControllerTests {
    private val service = mock<DataBackupService>()
    private val controller = DataBackupController(service, ResponseBuilder())

    @Test
    fun `下载备份时同步写出资源内容并在流关闭后清理临时目录`(@TempDir workRoot: Path) {
        val sql = workRoot.resolve("cartask-backup-20260913-220000.sql")
        Files.writeString(sql, "-- carTask 数据备份\nINSERT INTO \"users\" VALUES (1);\n")
        whenever(service.export(false)).thenReturn(
            DataBackupService.Artifact(
                path = sql,
                filename = sql.fileName.toString(),
                contentType = "application/sql",
                cleanupRoot = workRoot,
                stats = stats(),
            ),
        )

        val response = controller.export(false)

        assertEquals(200, response.statusCode.value())
        assertEquals("application/sql", response.headers.contentType.toString())
        assertEquals(Files.size(sql), response.headers.contentLength)
        assertTrue(
            response.headers.getFirst("Content-Disposition")!!.contains("cartask-backup-20260913-220000.sql"),
            "响应头里要带上文件名，前端才还原得出下载名",
        )

        val body = response.body
        assertFalse(
            body is StreamingResponseBody,
            "下载不能走 StreamingResponseBody：异步派发会再走一遍过滤器链，无状态鉴权下上下文已丢失，" +
                "AuthorizationFilter 会在响应写出后抛 AccessDeniedException",
        )
        assertTrue(body is Resource, "响应体应当是同步写出的资源，实际：${body?.javaClass}")

        // 先留一份预期内容：流一关闭临时目录就被删了，之后再读原文件会 NoSuchFileException。
        val expected = Files.readString(sql)
        val content = body!!.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        assertEquals(expected, content)
        assertFalse(Files.exists(workRoot), "响应流关闭后临时目录必须被删掉，服务器上不留档")
    }

    @Test
    fun `打包附件时返回压缩包类型`(@TempDir workRoot: Path) {
        val archive = workRoot.resolve("cartask-backup-20260913-220000.zip")
        Files.write(archive, byteArrayOf(0x50, 0x4B, 0x03, 0x04))
        whenever(service.export(true)).thenReturn(
            DataBackupService.Artifact(
                path = archive,
                filename = archive.fileName.toString(),
                contentType = "application/zip",
                cleanupRoot = workRoot,
                stats = stats(),
            ),
        )

        val response = controller.export(true)

        assertEquals("application/zip", response.headers.contentType.toString())
        response.body!!.inputStream.use { it.readBytes() }
        assertFalse(Files.exists(workRoot))
    }

    private fun stats() = DataBackupService.Stats(
        tableCount = 1,
        rowCount = 1,
        sqlBytes = 1,
        fileCount = 0,
        fileBytes = 0,
        missingFiles = 0,
        archiveIncluded = false,
        archiveBytes = 0,
        durationMillis = 1,
    )
}
