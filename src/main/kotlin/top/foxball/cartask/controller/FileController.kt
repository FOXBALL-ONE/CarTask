package top.foxball.cartask.controller

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.core.io.FileSystemResource
import org.springframework.http.CacheControl
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.InvalidMediaTypeException
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import top.foxball.cartask.service.FileService
import top.foxball.cartask.shared.Response
import top.foxball.cartask.shared.ResponseBuilder
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.util.UUID

@RestController
@RequestMapping("/api/files")
/** 文件上传、元数据查询和下载接口。 */
class FileController(
    private val fileService: FileService,
    private val responseBuilder: ResponseBuilder,
) {
    /** 接收单个 multipart 文件并返回文件元数据。 */
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN')) and hasAuthority('file:upload')")
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

    /** 返回指定文件的元数据和下载地址。 */
    @GetMapping("/{id}")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN')) and hasAuthority('file:read')")
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

    /** 以附件形式下载指定文件并恢复原始文件名。 */
    @GetMapping("/{id}/download")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN')) and hasAuthority('file:read')")
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
