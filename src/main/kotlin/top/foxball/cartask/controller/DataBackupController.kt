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

/**
 * 数据备份接口。
 *
 * 整库数据加全部附件，是系统里最敏感的一份产物，因此不挂进任何数据范围判定，
 * 直接用「超级管理员 + backup:manage」把门：平台管理与部门管理都不该拿到它，
 * 数据范围对这类全局导出也没有意义。
 */
data class BackupProgressData(
    /** IDLE / COUNTING / DUMPING / ARCHIVING / FINISHED / FAILED。 */
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
class DataBackupController(
    private val dataBackupService: DataBackupService,
    private val backupProgressService: BackupProgressService,
    private val responseBuilder: ResponseBuilder,
) {
    @GetMapping("/summary")
    @PreAuthorize(BACKUP_AUTHORIZATION)
            /**
             * summary：执行当前模块中的业务操作。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
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
     * 当前备份进度，供页面按秒轮询画进度条。
     *
     * 路径在 `/api/backup/` 下，因此备份期间也放行（见 MaintenanceFilter）——否则生成过程中
     * 前端一个字节都拿不到，进度条只会停在起点。
     */
    @GetMapping("/progress")
    @PreAuthorize(BACKUP_AUTHORIZATION)
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

    /**
     * 生成并下载备份。
     *
     * [includeFiles] 为 true 时产出 zip（SQL + 附件 + 清单），为 false 时只产出 SQL。
     * 两种情况下 SQL 都会生成——压缩包只是多带上了附件，不是 SQL 的替代品。
     *
     * 产物先落到临时目录再写出，是为了不让上百 MB 的备份在堆里过一遍；
     * 临时目录在响应流关闭时删除，服务器上不留档。
     *
     * **不要改成 StreamingResponseBody**：那会让请求变成异步，容器在流写完后还要做一次 ASYNC 派发
     * 再走一遍过滤器链，而本项目的鉴权是无状态的（JWT + NullSecurityContextRepository）——
     * 第二次派发时 JwtAuthenticationFilter 因为 OncePerRequestFilter 的已处理标记不再执行，
     * SecurityContextHolderFilter 又读不到任何上下文，AuthorizationFilter 就会判定匿名并抛
     * AccessDeniedException；此时响应体已经写出，状态码改不了也回不去，只会在日志里留下两条 ERROR。
     */
    @GetMapping("/export")
    @PreAuthorize(BACKUP_AUTHORIZATION)
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

    /**
     * deleteQuietly：删除、清理或撤销相关数据。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param root 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    private fun deleteQuietly(root: Path) {
        runCatching {
            if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return
            Files.walk(root).use { paths ->
                // 先深后浅：父目录在校举流里也会出现，顺序反了会因为目录非空而删不掉。
                paths.sorted { left, right -> right.compareTo(left) }.forEach { Files.deleteIfExists(it) }
            }
        }
    }

    /**
     * 备份产物对应的只读资源，输入流关闭时把整个临时目录删掉。
     *
     * ResourceHttpMessageConverter 写完响应体后一定会在 finally 里关闭这个流，所以清理不需要额外的
     * 生命周期钩子；这也是同步写出相对于异步写法唯一需要自己接上的地方。
     */
    private inner class BackupArtifactResource(
        private val file: Path,
        private val cleanupRoot: Path,
    ) : AbstractResource() {
        /**
         * getDescription：查询或读取相关数据。
         *
         * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
         * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
         * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
         */
        override fun getDescription(): String = "备份产物 [$file]"

        /**
         * getFilename：查询或读取相关数据。
         *
         * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
         * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
         * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
         */
        override fun getFilename(): String = file.fileName.toString()

        /**
         * getFile：查询或读取相关数据。
         *
         * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
         * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
         * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
         */
        override fun getFile(): File = file.toFile()

        /**
         * contentLength：执行当前模块中的业务操作。
         *
         * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
         * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
         * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
         */
        override fun contentLength(): Long = Files.size(file)

        /**
         * getInputStream：查询或读取相关数据。
         *
         * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
         * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
         * @param object 参与本次处理的输入参数。
         * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
         */
        override fun getInputStream(): InputStream = object : FilterInputStream(Files.newInputStream(file)) {
            /**
             * close：执行当前模块中的业务操作。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
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
        /** 角色与权限码同时要求：权限码管字典与界面，角色管「谁能碰这份产物」。 */
        const val BACKUP_AUTHORIZATION = "hasRole('SUPER_ADMIN') and hasAuthority('backup:manage')"
    }
}
