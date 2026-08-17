package top.foxball.cartask.service

import org.springframework.web.multipart.MultipartFile
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.UUID

/** 本地文件存储、元数据查询和下载资源解析服务。 */
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

    /** 上传文件并返回其元数据与下载地址。 */
    fun upload(file: MultipartFile): FileData

    /** 按文件 ID 查询元数据。 */
    fun get(id: UUID): FileData

    /** 解析下载所需的本地文件资源。 */
    fun openDownload(id: UUID): DownloadData
}
