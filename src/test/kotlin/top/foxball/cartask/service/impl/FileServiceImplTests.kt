package top.foxball.cartask.service.impl

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.mock.web.MockMultipartFile
import org.springframework.transaction.support.TransactionOperations
import top.foxball.cartask.config.FileProperties
import top.foxball.cartask.entity.StoredFile
import top.foxball.cartask.handler.ParamErrorException
import top.foxball.cartask.handler.ResourceNotFoundException
import top.foxball.cartask.repository.StoredFileRepository
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Optional
import java.util.UUID
import kotlin.io.path.createTempDirectory
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileServiceImplTests {
    @Test
    fun `stores under date directory with UUID name and preserves original download name`() {
        val root = createTempDirectory("file-service-")
        val repository = mock(StoredFileRepository::class.java)
        val captured = ArgumentCaptor.forClass(StoredFile::class.java)
        `when`(repository.saveAndFlush(any(StoredFile::class.java))).thenAnswer { invocation ->
            invocation.getArgument<StoredFile>(0)
        }
        val service = service(root, repository)
        val payload = "file content".toByteArray(StandardCharsets.UTF_8)

        val data = service.upload(MockMultipartFile("file", "C:\\upload\\报价单.PDF", "application/pdf", payload))

        org.mockito.Mockito.verify(repository).saveAndFlush(captured.capture())
        val metadata = captured.value
        val datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))
        val expectedPath = root.resolve(datePath).resolve("${data.id}.PDF")
        assertEquals("报价单.PDF", data.originalFilename)
        assertEquals("https://files.example.com/shopmall/api/files/${data.id}/download", data.downloadUrl)
        assertEquals("$datePath/${data.id}.PDF", metadata.relativePath)
        assertEquals("${data.id}.PDF", metadata.storedFilename)
        assertEquals("e0ac3601005dfa1864f5392aabaf7d898b1b5bab854f1acb4491bcd806b76b0c", metadata.sha256)
        assertContentEquals(payload, Files.readAllBytes(expectedPath))

        `when`(repository.findById(data.id)).thenReturn(Optional.of(metadata))
        val download = service.openDownload(data.id)
        assertEquals(expectedPath, download.path)
        assertEquals("报价单.PDF", download.originalFilename)
        assertEquals(payload.size.toLong(), download.sizeBytes)
    }

    @Test
    fun `removes stored content when metadata persistence fails`() {
        val root = createTempDirectory("file-service-")
        val repository = mock(StoredFileRepository::class.java)
        `when`(repository.saveAndFlush(any(StoredFile::class.java))).thenThrow(DataIntegrityViolationException("database unavailable"))
        val service = service(root, repository)

        assertThrows<DataIntegrityViolationException> {
            service.upload(MockMultipartFile("file", "report.pdf", "application/pdf", byteArrayOf(1, 2, 3)))
        }

        Files.walk(root).use { paths ->
            assertFalse(paths.anyMatch { path -> path.fileName.toString().endsWith(".pdf") || path.fileName.toString().endsWith(".uploading") })
        }
    }

    @Test
    fun `rejects a relative path that escapes the configured root`() {
        val root = createTempDirectory("file-service-")
        val repository = mock(StoredFileRepository::class.java)
        val id = UUID.randomUUID()
        val metadata = storedFile(id, "../../outside.txt")
        `when`(repository.findById(id)).thenReturn(Optional.of(metadata))
        val service = service(root, repository)

        assertThrows<ResourceNotFoundException> { service.openDownload(id) }
    }

    @Test
    fun `rejects invalid suffixes and does not create content`() {
        val root = createTempDirectory("file-service-")
        val repository = mock(StoredFileRepository::class.java)
        val service = service(root, repository)

        assertThrows<ParamErrorException> {
            service.upload(MockMultipartFile("file", "report.bad-extension", "application/octet-stream", byteArrayOf(1)))
        }

        Files.walk(root).use { paths -> assertTrue(paths.count() == 1L) }
    }

    private fun service(root: Path, repository: StoredFileRepository): FileServiceImpl = FileServiceImpl(
        fileRepository = repository,
        properties = FileProperties(root.toString(), "https://files.example.com/shopmall/"),
        transactionOperations = TransactionOperations.withoutTransaction(),
    )

    private fun storedFile(id: UUID, relativePath: String): StoredFile = StoredFile().apply {
        this.id = id
        originalFilename = "outside.txt"
        storedFilename = "$id.txt"
        this.relativePath = relativePath
        contentType = "text/plain"
        sizeBytes = 1
        sha256 = "0".repeat(64)
        createdAt = java.time.LocalDateTime.now()
    }
}
