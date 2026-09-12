package top.foxball.cartask.service.impl

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionOperations
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.util.UriComponentsBuilder
import top.foxball.cartask.authentication.CurrentUserPrincipal
import top.foxball.cartask.config.FileProperties
import top.foxball.cartask.entity.StoredFile
import top.foxball.cartask.handler.ParamErrorException
import top.foxball.cartask.handler.ResourceNotFoundException
import top.foxball.cartask.repository.StoredFileRepository
import top.foxball.cartask.scope.DataScopeResolver
import top.foxball.cartask.scope.DepartmentLinkResolver
import top.foxball.cartask.scope.ScopeQuerySupport
import top.foxball.cartask.service.FileService
import top.foxball.cartask.audit.AuditAction
import top.foxball.cartask.audit.AuditCommand
import top.foxball.cartask.audit.AuditService
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.HexFormat
import java.util.UUID

@Service
/** 将上传文件写入本地存储并维护元数据的一致性。 */
class FileServiceImpl(
    private val fileRepository: StoredFileRepository,
    private val properties: FileProperties,
    private val transactionOperations: TransactionOperations,
    private val dataScopeResolver: DataScopeResolver,
    private val scopeQuerySupport: ScopeQuerySupport,
    private val departmentLinkResolver: DepartmentLinkResolver,
    private val auditService: AuditService? = null,
) : FileService {
    private val remoteHttpClient = HttpClient.newBuilder()
        .connectTimeout(java.time.Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    /** 先写入磁盘，再持久化元数据；持久化失败时清理物理文件。 */
    override fun upload(file: MultipartFile): FileService.FileData {
        require(!file.isEmpty) { "文件不能为空" }
        val storedUpload = storeUpload(file)
        applyUploader(storedUpload.metadata)
        try {
            val saved = transactionOperations.execute {
                val persisted = fileRepository.saveAndFlush(storedUpload.metadata)
                auditService?.record(
                    AuditCommand(
                        AuditAction.FILE_UPLOADED,
                        "stored_file",
                        persisted.id.toString(),
                        targetSummary = mapOf("original_filename" to persisted.originalFilename, "size_bytes" to persisted.sizeBytes, "content_type" to persisted.contentType),
                    ),
                )
                persisted
            }
            return fileData(saved)
        } catch (ex: Exception) {
            deleteQuietly(storedUpload.path)
            throw ex
        }
    }

    override fun importRemote(url: String, origin: FileService.FileOrigin?): FileService.FileData {
        val uri = try {
            URI.create(url.trim())
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("远程图片地址不合法")
        }
        require(uri.scheme == "http" || uri.scheme == "https") { "远程图片地址必须使用 HTTP(S)" }
        val response = try {
            remoteHttpClient.send(
                HttpRequest.newBuilder(uri)
                    .timeout(java.time.Duration.ofSeconds(30))
                    .header("Accept", "image/*")
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofByteArray(),
            )
        } catch (exception: Exception) {
            throw IllegalStateException("下载远程图片失败", exception)
        }
        require(response.statusCode() in 200..299) { "下载远程图片失败：HTTP ${response.statusCode()}" }
        val body = response.body()
        require(body.isNotEmpty()) { "远程图片内容为空" }
        require(body.size <= MAX_REMOTE_IMAGE_BYTES) { "远程图片超过大小限制" }
        val contentType = response.headers().firstValue("Content-Type")
            .orElse(null)
            ?.substringBefore(';')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        require(contentType == null || contentType.startsWith("image/", ignoreCase = true)) {
            "远程资源不是图片"
        }
        val filename = URI(uri.toString()).path.substringAfterLast('/').takeIf { it.isNotBlank() } ?: "capture.jpg"
        val storedUpload = body.inputStream().use { input ->
            storeContent(
                input = input,
                safeFilename = safeFilename(filename),
                contentType = contentType,
            )
        }
        return try {
            origin?.let { applyOrigin(storedUpload.metadata, it) }
            val saved = transactionOperations.execute {
                val persisted = fileRepository.saveAndFlush(storedUpload.metadata)
                auditService?.record(
                    AuditCommand(
                        AuditAction.FILE_UPLOADED,
                        "stored_file",
                        persisted.id.toString(),
                        targetSummary = mapOf("original_filename" to persisted.originalFilename, "size_bytes" to persisted.sizeBytes, "content_type" to persisted.contentType, "source_url" to url),
                    ),
                )
                persisted
            }
            fileData(saved)
        } catch (exception: Exception) {
            deleteQuietly(storedUpload.path)
            throw exception
        }
    }

    /** 将已存储的元数据转换为对外返回数据。 */
    override fun get(id: UUID): FileService.FileData = fileData(findVisibleFile(id))
    /** 验证记录和文件均存在后，返回下载资源描述。 */
    override fun openDownload(id: UUID): FileService.DownloadData {
        val storedFile = findVisibleFile(id)
        val path = resolveStoredPath(storedFile.relativePath)
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw ResourceNotFoundException("文件不存在")
        }
        auditService?.record(
            AuditCommand(
                AuditAction.FILE_DOWNLOADED,
                "stored_file",
                id.toString(),
                targetSummary = mapOf("original_filename" to storedFile.originalFilename, "size_bytes" to storedFile.sizeBytes),
            ),
        )
        return FileService.DownloadData(
            path = path,
            originalFilename = storedFile.originalFilename,
            contentType = storedFile.contentType,
            sizeBytes = storedFile.sizeBytes,
        )
    }

    /** 将上传流写入日期目录，并在写入过程中计算摘要和大小。 */
    private fun storeUpload(file: MultipartFile): StoredUpload {
        val safeFilename = safeFilename(file.originalFilename)
        return file.inputStream.use { input ->
            storeContent(input, safeFilename, file.contentType)
        }
    }

    private fun storeContent(input: java.io.InputStream, safeFilename: SafeFilename, contentType: String?): StoredUpload {
        val date = LocalDate.now()
        val datePath = date.format(DATE_PATH_FORMATTER)
        val directory = properties.rootPath.resolve(datePath)
        Files.createDirectories(directory)

        val id = UUID.randomUUID()
        val storedFilename = "$id${safeFilename.extension}"
        val target = directory.resolve(storedFilename)
        val temporary = directory.resolve(".$id.uploading")
        var temporaryCreated = false
        var moved = false
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var sizeBytes = 0L
            Files.newOutputStream(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { output ->
                temporaryCreated = true
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    digest.update(buffer, 0, count)
                    sizeBytes += count
                }
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, target)
            }
            moved = true
            val metadata = StoredFile().apply {
                this.id = id
                originalFilename = safeFilename.value
                this.storedFilename = storedFilename
                relativePath = "$datePath/$storedFilename"
                this.contentType = contentType?.trim()?.takeIf { it.isNotEmpty() }?.take(255)
                this.sizeBytes = sizeBytes
                sha256 = HexFormat.of().formatHex(digest.digest())
                createdAt = LocalDateTime.now()
            }
            return StoredUpload(metadata, target)
        } catch (ex: Exception) {
            if (temporaryCreated) deleteQuietly(temporary)
            if (moved) deleteQuietly(target)
            throw ex
        }
    }

    /** 根据元数据构造包含绝对下载地址的文件数据。 */
    private fun fileData(storedFile: StoredFile): FileService.FileData = FileService.FileData(
        id = storedFile.id,
        originalFilename = storedFile.originalFilename,
        contentType = storedFile.contentType,
        sizeBytes = storedFile.sizeBytes,
        downloadUrl = UriComponentsBuilder.fromUriString(properties.downloadBaseUrl)
            .pathSegment("api", "files", storedFile.id.toString(), "download")
            .build()
            .toUriString(),
        createdAt = storedFile.createdAt,
    )

    /** 按 ID 查询元数据；不存在时统一转换为资源不存在错误。 */
    private fun findFile(id: UUID): StoredFile = fileRepository.findById(id)
        .orElseThrow { ResourceNotFoundException("文件不存在") }

    /**
     * 按当前数据范围取文件。
     *
     * 文件表原先没有归属字段，任何 `file:read` 持有者只要知道 UUID 就能下载任意附件，
     * 包括别人车辆的进出抓拍。范围外与不存在用同一个错误，避免用响应差异探测文件是否存在。
     */
    private fun findVisibleFile(id: UUID): StoredFile {
        val storedFile = findFile(id)
        val scope = dataScopeResolver.current()
        // 不受限范围直接放行，省掉逐条的业务归属反查。
        if (!scope.unrestricted && !scopeQuerySupport.fileVisible(scope, storedFile)) {
            throw ResourceNotFoundException("文件不存在")
        }
        return storedFile
    }

    /**
     * 落上上传者与其当前工作部门。
     *
     * 部门归属取上传者的当前工作部门；同步任务从外部平台拉取的图片没有登录主体，
     * 归属由调用方通过 [FileService.FileOrigin] 显式传入。
     */
    private fun applyUploader(metadata: StoredFile) {
        val principal = SecurityContextHolder.getContext().authentication?.principal as? CurrentUserPrincipal ?: return
        metadata.uploadedByUserId = principal.userId
        principal.workingDepartmentId
            ?.let { departmentId -> departmentLinkResolver.snapshot().codesOf(setOf(departmentId)).singleOrNull() }
            ?.let { metadata.departmentCode = it }
    }

    private fun applyOrigin(metadata: StoredFile, origin: FileService.FileOrigin) {
        origin.departmentCode?.let { metadata.departmentCode = it }
        origin.businessType?.let { metadata.businessType = it }
        origin.businessId?.let { metadata.businessId = it }
    }

    override fun linkBusiness(id: UUID, businessType: String, businessId: String) {
        val storedFile = findFile(id)
        storedFile.businessType = businessType
        storedFile.businessId = businessId
        fileRepository.save(storedFile)
    }

    /** 将数据库相对路径安全地限制在配置的文件根目录内。 */
    private fun resolveStoredPath(relativePath: String): Path {
        val relative = try {
            Path.of(relativePath).normalize()
        } catch (_: InvalidPathException) {
            throw ResourceNotFoundException("文件不存在")
        }
        if (relative.isAbsolute) throw ResourceNotFoundException("文件不存在")
        return properties.rootPath.resolve(relative).normalize()
            .also { resolved ->
                if (!resolved.startsWith(properties.rootPath)) throw ResourceNotFoundException("文件不存在")
            }
    }

    /** 清理客户端文件名并验证其长度、控制字符和后缀。 */
    private fun safeFilename(submittedFilename: String?): SafeFilename {
        val value = submittedFilename
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: throw ParamErrorException("上传文件必须包含文件名")
        if (value.length > MAX_ORIGINAL_FILENAME_LENGTH || value.any { it.code < 32 || it.code == 127 }) {
            throw ParamErrorException("文件名不合法")
        }
        val extension = value.substringAfterLast('.', missingDelimiterValue = "")
            .takeIf { value.lastIndexOf('.') > 0 && it.isNotEmpty() }
            ?.also { if (!EXTENSION_PATTERN.matches(it)) throw ParamErrorException("文件后缀不合法") }
            ?.let { ".${it}" }
            ?: ""
        return SafeFilename(value, extension)
    }

    /** 在补偿或失败清理时忽略物理文件删除异常。 */
    private fun deleteQuietly(path: Path) {
        runCatching { Files.deleteIfExists(path) }
    }

    private data class SafeFilename(
        val value: String,
        val extension: String,
    )

    private data class StoredUpload(
        val metadata: StoredFile,
        val path: Path,
    )

    private companion object {
        val DATE_PATH_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")
        val EXTENSION_PATTERN = Regex("[A-Za-z0-9]{1,20}")
        const val MAX_ORIGINAL_FILENAME_LENGTH = 255
        const val MAX_REMOTE_IMAGE_BYTES = 10 * 1024 * 1024
    }
}
