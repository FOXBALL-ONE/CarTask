package top.foxball.cartask.controller

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.CacheControl
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import top.foxball.cartask.service.DataBackupService
import top.foxball.cartask.shared.Response
import top.foxball.cartask.shared.ResponseBuilder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/**
 * 数据备份接口。
 *
 * 整库数据加全部附件，是系统里最敏感的一份产物，因此不挂进任何数据范围判定，
 * 直接用「超级管理员 + backup:manage」把门：平台管理与部门管理都不该拿到它，
 * 数据范围对这类全局导出也没有意义。
 */
@RestController
@RequestMapping("/api/backup")
class DataBackupController(
    private val dataBackupService: DataBackupService,
    private val responseBuilder: ResponseBuilder,
) {
    @GetMapping("/summary")
    @PreAuthorize(BACKUP_AUTHORIZATION)
    fun summary(): ResponseEntity<Response> {
        data class Response(
            @param:JsonProperty("database_product") val databaseProduct: String,
            @param:JsonProperty("table_count") val tableCount: Int,
            @param:JsonProperty("file_count") val fileCount: Long,
            @param:JsonProperty("file_bytes") val fileBytes: Long,
            @param:JsonProperty("storage_root") val storageRoot: String,
        )

        val summary = dataBackupService.summary()
        val rs = Response(
            summary.databaseProduct,
            summary.tableCount,
            summary.fileCount,
            summary.fileBytes,
            summary.storageRoot,
        )
        return responseBuilder.ok().data(rs).build()
    }

    /**
     * 生成并下载备份。
     *
     * [includeFiles] 为 true 时产出 zip（SQL + 附件 + 清单），为 false 时只产出 SQL。
     * 两种情况下 SQL 都会生成——压缩包只是多带上了附件，不是 SQL 的替代品。
     *
     * 产物先落到临时目录再流式写出，是为了不让上百 MB 的备份在堆里过一遍；
     * 临时目录在响应写完之后立刻删除，服务器上不留档。
     */
    @GetMapping("/export")
    @PreAuthorize(BACKUP_AUTHORIZATION)
    fun export(
        @RequestParam(name = "include_files", defaultValue = "false") includeFiles: Boolean,
    ): ResponseEntity<StreamingResponseBody> {
        val artifact = dataBackupService.export(includeFiles)
        val disposition = ContentDisposition.attachment()
            .filename(artifact.filename, StandardCharsets.UTF_8)
            .build()
        val body = StreamingResponseBody { output ->
            try {
                Files.newInputStream(artifact.path).use { it.transferTo(output) }
            } finally {
                deleteQuietly(artifact.cleanupRoot)
            }
        }
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(artifact.contentType))
            .cacheControl(CacheControl.noStore())
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
            .header("X-Content-Type-Options", "nosniff")
            .body(body)
    }

    private fun deleteQuietly(root: Path) {
        runCatching {
            if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return
            Files.walk(root).use { paths ->
                // 先深后浅：父目录在校举流里也会出现，顺序反了会因为目录非空而删不掉。
                paths.sorted { left, right -> right.compareTo(left) }.forEach { Files.deleteIfExists(it) }
            }
        }
    }

    private companion object {
        /** 角色与权限码同时要求：权限码管字典与界面，角色管「谁能碰这份产物」。 */
        const val BACKUP_AUTHORIZATION = "hasRole('SUPER_ADMIN') and hasAuthority('backup:manage')"
    }
}
