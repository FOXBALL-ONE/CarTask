package top.foxball.cartask.controller

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.core.io.AbstractResource
import org.springframework.core.io.Resource
import org.springframework.http.*
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import top.foxball.cartask.service.BackupProgressService
import top.foxball.cartask.service.DataBackupService
import top.foxball.cartask.shared.Response
import top.foxball.cartask.shared.ResponseBuilder
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.time.LocalDateTime


data class BackupProgressData(
    
    val phase: String,
    val label: String,
    val percent: Int,
    @param:JsonProperty("tables_done") val tablesDone: Int,
    @param:JsonProperty("tables_total") val tablesTotal: Int,
    @param:JsonProperty("rows_done") val rowsDone: Long,
    @param:JsonProperty("rows_total") val rowsTotal: Long,
    @param:JsonProperty("files_done") val filesDone: Int,
    @param:JsonProperty("files_total") val filesTotal: Int,
    @param:JsonProperty("started_at") val startedAt: LocalDateTime?,
    @param:JsonProperty("finished_at") val finishedAt: LocalDateTime?,
    val message: String?,
)

@RestController
@RequestMapping("/api/backup")
/** class DataBackupController：Web 控制器，负责接收 HTTP 请求、调用领域服务并构造统一响应。 */
class DataBackupController(
    private val dataBackupService: DataBackupService,
    private val backupProgressService: BackupProgressService,
    private val responseBuilder: ResponseBuilder,
) {
    @GetMapping("/summary")
    @PreAuthorize(BACKUP_AUTHORIZATION)
            
            
            /** summary：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
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
    
    
    @GetMapping("/progress")
    @PreAuthorize(BACKUP_AUTHORIZATION)
            /** progress：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun progress(): ResponseEntity<Response> {
        val progress = backupProgressService.snapshot()
        val rs = BackupProgressData(
            phase = progress.phase.name,
            label = progress.label,
            percent = progress.percent,
            tablesDone = progress.tablesDone,
            tablesTotal = progress.tablesTotal,
            rowsDone = progress.rowsDone,
            rowsTotal = progress.rowsTotal,
            filesDone = progress.filesDone,
            filesTotal = progress.filesTotal,
            startedAt = progress.startedAt,
            finishedAt = progress.finishedAt,
            message = progress.message,
        )
        return responseBuilder.ok().data(rs).build()
    }
    
    
    @GetMapping("/export")
    @PreAuthorize(BACKUP_AUTHORIZATION)
            /** export：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun export(
        @RequestParam(name = "include_files", defaultValue = "false") includeFiles: Boolean,
    ): ResponseEntity<Resource> {
        val artifact = dataBackupService.export(includeFiles)
        val disposition = ContentDisposition.attachment()
            .filename(artifact.filename, StandardCharsets.UTF_8)
            .build()
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(artifact.contentType))
            .contentLength(Files.size(artifact.path))
            .cacheControl(CacheControl.noStore())
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
            .header("X-Content-Type-Options", "nosniff")
            .body(BackupArtifactResource(artifact.path, artifact.cleanupRoot))
    }
    
    
    private fun deleteQuietly(root: Path) {
        runCatching {
            if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return
            Files.walk(root).use { paths ->
                paths.sorted { left, right -> right.compareTo(left) }.forEach { Files.deleteIfExists(it) }
            }
        }
    }
    
    
    private inner class BackupArtifactResource(
        private val file: Path,
        private val cleanupRoot: Path,
    ) : AbstractResource() {
        
        
        override fun getDescription(): String = "备份产物 [$file]"
        
        
        override fun getFilename(): String = file.fileName.toString()
        
        
        override fun getFile(): File = file.toFile()
        
        
        override fun contentLength(): Long = Files.size(file)
        
        
        override fun getInputStream(): InputStream = object : FilterInputStream(Files.newInputStream(file)) {
            
            
            override fun close() {
                try {
                    super.close()
                } finally {
                    deleteQuietly(cleanupRoot)
                }
            }
        }
    }
    
    private companion object {
        
        const val BACKUP_AUTHORIZATION = "hasRole('SUPER_ADMIN') and hasAuthority('backup:manage')"
    }
}
