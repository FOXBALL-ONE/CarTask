package top.foxball.cartask.controller

import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import top.foxball.cartask.service.FileService
import top.foxball.cartask.shared.ResponseBuilder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.UUID
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals

class FileControllerTests {
    @Test
    fun `download response restores a UTF-8 original filename`() {
        val root = createTempDirectory("file-controller-")
        val path = root.resolve("stored.pdf")
        val payload = "content".toByteArray(StandardCharsets.UTF_8)
        Files.write(path, payload)
        val id = UUID.randomUUID()
        val service = mock(FileService::class.java)
        `when`(service.openDownload(id)).thenReturn(
            FileService.DownloadData(path, "报价单.pdf", "application/pdf", payload.size.toLong()),
        )
        val controller = FileController(service, ResponseBuilder())

        val response = controller.download(id)

        val contentDisposition = ContentDisposition.parse(response.headers.getFirst(HttpHeaders.CONTENT_DISPOSITION)!!)
        assertEquals("报价单.pdf", contentDisposition.filename)
        assertEquals("no-store", response.headers.cacheControl)
        assertEquals("nosniff", response.headers.getFirst("X-Content-Type-Options"))
        assertEquals(payload.size.toLong(), response.headers.contentLength)
    }
}
