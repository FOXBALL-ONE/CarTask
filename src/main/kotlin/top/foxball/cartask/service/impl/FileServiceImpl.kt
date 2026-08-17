package top.foxball.cartask.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionOperations
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.util.UriComponentsBuilder
import top.foxball.cartask.config.FileProperties
import top.foxball.cartask.entity.StoredFile
import top.foxball.cartask.handler.ParamErrorException
import top.foxball.cartask.handler.ResourceNotFoundException
import top.foxball.cartask.repository.StoredFileRepository
import top.foxball.cartask.service.FileService
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
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
) : FileService {
    /** 先写入磁盘，再持久化元数据；持久化失败时清理物理文件。 */
    override fun upload(file: MultipartFile): FileService.FileData {
        require(!file.isEmpty) { "文件不能为空" }
        val storedUpload = storeUpload(file)
        try {
            val saved = transactionOperations.execute {
                fileRepository.saveAndFlush(storedUpload.metadata)
            }
            return fileData(saved)
        } catch (ex: Exception) {
            deleteQuietly(storedUpload.path)
            throw ex
        }
    }

    /** 将已存储的元数据转换为对外返回数据。 */
    override fun get(id: UUID): FileService.FileData = fileData(findFile(id))

    /** 验证记录和文件均存在后，返回下载资源描述。 */
    override fun openDownload(id: UUID): FileService.DownloadData {
        val storedFile = findFile(id)
        val path = resolveStoredPath(storedFile.relativePath)
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw ResourceNotFoundException("文件不存在")
        }
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
        val date = LocalDate.now()
        val datePath = date.format(DATE_PATH_FORMATTER)
        val directory = properties.rootPath.resolve(datePath)
        Files.createDirectories(directory)

        repeat(MAX_STORAGE_NAME_ATTEMPTS) {
            val id = UUID.randomUUID()
            val storedFilename = "$id${safeFilename.extension}"
            val target = directory.resolve(storedFilename)
            val temporary = directory.resolve(".$id.uploading")
            var temporaryCreated = false
            var moved = false
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                var sizeBytes = 0L
                file.inputStream.use { input ->
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
                    contentType = file.contentType?.trim()?.takeIf { it.isNotEmpty() }?.take(255)
                    this.sizeBytes = sizeBytes
                    sha256 = HexFormat.of().formatHex(digest.digest())
                    createdAt = LocalDateTime.now()
                }
                return StoredUpload(metadata, target)
            } catch (_: java.nio.file.FileAlreadyExistsException) {
                if (temporaryCreated) deleteQuietly(temporary)
                if (moved) deleteQuietly(target)
            } catch (ex: Exception) {
                if (temporaryCreated) deleteQuietly(temporary)
                if (moved) deleteQuietly(target)
                throw ex
            }
        }
        throw IllegalStateException("Unable to allocate a unique file name.")
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
        const val MAX_STORAGE_NAME_ATTEMPTS = 5
    }
}
