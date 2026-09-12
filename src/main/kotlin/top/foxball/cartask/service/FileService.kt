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

    /** 远程导入时的业务归属，用于把图片限定在对应范围的可见集合内。 */
    data class FileOrigin(
        val departmentCode: String? = null,
        val businessType: String? = null,
        val businessId: String? = null,
    )

    /** 上传文件并返回其元数据与下载地址。 */
    fun upload(file: MultipartFile): FileData

    /** 下载远程资源并写入本地文件存储，返回本地下载地址。 */
    fun importRemote(url: String, origin: FileOrigin? = null): FileData

    /** 把已上传的文件关联到业务对象，使业务归属人也能取到该文件。 */
    fun linkBusiness(id: UUID, businessType: String, businessId: String)

    /** 按文件 ID 查询元数据。 */
    fun get(id: UUID): FileData

    /** 解析下载所需的本地文件资源。 */
    fun openDownload(id: UUID): DownloadData
}
