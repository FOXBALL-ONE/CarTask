package top.foxball.cartask.controller

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.core.io.FileSystemResource
import org.springframework.http.*
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import top.foxball.cartask.service.FileService
import top.foxball.cartask.shared.Response
import top.foxball.cartask.shared.ResponseBuilder
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.util.*

@RestController
@RequestMapping("/api/files")

/** class FileController：Web 控制器，负责接收 HTTP 请求、调用领域服务并构造统一响应。 */
class FileController(
    private val fileService: FileService,
    private val responseBuilder: ResponseBuilder,
) {
    
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('file:upload')")
            /** upload：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun upload(@RequestPart("file") file: MultipartFile): ResponseEntity<Response> {
        data class Response(
            val id: UUID,
            @param:JsonProperty("original_filename") val originalFilename: String,
            @param:JsonProperty("content_type") val contentType: String?,
            @param:JsonProperty("size_bytes") val sizeBytes: Long,
            @param:JsonProperty("download_url") val downloadUrl: String,
            @param:JsonProperty("created_at") val createdAt: LocalDateTime,
        )
        
        val fileData = fileService.upload(file)
        val rs = Response(
            fileData.id,
            fileData.originalFilename,
            fileData.contentType,
            fileData.sizeBytes,
            fileData.downloadUrl,
            fileData.createdAt,
        )
        return responseBuilder.created().data(rs).build()
    }
    
    
    @GetMapping("/{id}")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN') or hasRole('USER')) and hasAuthority('file:read')")
            /** get：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun get(@PathVariable id: UUID): ResponseEntity<Response> {
        data class Response(
            val id: UUID,
            @param:JsonProperty("original_filename") val originalFilename: String,
            @param:JsonProperty("content_type") val contentType: String?,
            @param:JsonProperty("size_bytes") val sizeBytes: Long,
            @param:JsonProperty("download_url") val downloadUrl: String,
            @param:JsonProperty("created_at") val createdAt: LocalDateTime,
        )
        
        val fileData = fileService.get(id)
        val rs = Response(
            fileData.id,
            fileData.originalFilename,
            fileData.contentType,
            fileData.sizeBytes,
            fileData.downloadUrl,
            fileData.createdAt,
        )
        return responseBuilder.ok().data(rs).build()
    }
    
    
    @GetMapping("/{id}/download")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN') or hasRole('USER')) and hasAuthority('file:read')")
            /** download：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun download(@PathVariable id: UUID): ResponseEntity<FileSystemResource> {
        val fileData = fileService.openDownload(id)
        val mediaType = try {
            fileData.contentType?.let(MediaType::parseMediaType) ?: MediaType.APPLICATION_OCTET_STREAM
        } catch (_: InvalidMediaTypeException) {
            MediaType.APPLICATION_OCTET_STREAM
        }
        val contentDisposition = ContentDisposition.attachment()
            .filename(fileData.originalFilename, StandardCharsets.UTF_8)
            .build()
        return ResponseEntity.ok()
            .contentType(mediaType)
            .contentLength(fileData.sizeBytes)
            .cacheControl(CacheControl.noStore())
            .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
            .header("X-Content-Type-Options", "nosniff")
            .body(FileSystemResource(fileData.path))
    }
}
