package top.foxball.cartask.service

import org.springframework.web.multipart.MultipartFile
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.*


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
    
    
    data class FileOrigin(
        val departmentCode: String? = null,
        val businessType: String? = null,
        val businessId: String? = null,
    )
    
    
    fun upload(file: MultipartFile): FileData
    
    
    fun importRemote(url: String, origin: FileOrigin? = null): FileData
    
    
    fun linkBusiness(id: UUID, businessType: String, businessId: String)
    
    
    fun relinkBusiness(businessType: String, fromBusinessId: String, toBusinessId: String, departmentCode: String?)
    
    
    fun unlinkBusiness(businessType: String, businessId: String)
    
    
    fun get(id: UUID): FileData
    
    
    fun openDownload(id: UUID): DownloadData
}
