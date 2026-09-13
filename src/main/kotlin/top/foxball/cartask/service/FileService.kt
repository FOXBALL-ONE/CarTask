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

    /**
     * 把某业务对象已有的文件重新锚定到新的业务标识与部门。
     *
     * 业务标识（例如门禁人员改了人员编号）或部门变动后必须调用：
     * 文件既按业务标识判定归属，也按部门快照判定归属，不重新锚定就会让旧编号 / 旧部门继续能取到文件。
     */
    fun relinkBusiness(businessType: String, fromBusinessId: String, toBusinessId: String, departmentCode: String?)

    /**
     * 解除某业务对象的文件关联，并一并清掉部门归属。
     *
     * 业务对象被删除时调用。只清业务标识是不够的：按部门判定的可见性仍然成立，
     * 旧部门的用户与上传者拿到 UUID 后依然能下载——对已删人员的生物特征照片来说这是残留访问。
     */
    fun unlinkBusiness(businessType: String, businessId: String)

    /** 按文件 ID 查询元数据。 */
    fun get(id: UUID): FileData

    /** 解析下载所需的本地文件资源。 */
    fun openDownload(id: UUID): DownloadData
}
