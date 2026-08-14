package top.foxball.cartask.service

import org.springframework.web.multipart.MultipartFile
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.UUID

interface FileService {
    data class FileData(
        val id: UUID,
        val originalFilename: String,
        val contentType: String?,
        val sizeBytes: Long,
        val downloadUrl: String,
        val createdAt: LocalDateTime,
    )

    data class DownloadData(
        val path: Path,
        val originalFilename: String,
        val contentType: String?,
        val sizeBytes: Long,
    )

    fun upload(file: MultipartFile): FileData

    fun get(id: UUID): FileData

    fun openDownload(id: UUID): DownloadData
}
