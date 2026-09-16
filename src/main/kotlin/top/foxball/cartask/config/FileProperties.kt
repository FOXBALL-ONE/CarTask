package top.foxball.cartask.config

/**
 * FileProperties 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */

import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths


@ConfigurationProperties(prefix = "app.file")
data class FileProperties(
    val storageRoot: String = System.getProperty("user.dir"),
    val baseUrl: String = "",
) {
    val rootPath: Path = storageRoot.trim()
        .ifBlank { System.getProperty("user.dir") }
        .let(Paths::get)
        .toAbsolutePath()
        .normalize()
        .also {
            Files.createDirectories(it)
            require(Files.isDirectory(it)) { "FILE_STORAGE_ROOT must be a directory." }
            require(Files.isWritable(it)) { "FILE_STORAGE_ROOT must be writable." }
        }
    
    val downloadBaseUrl: String = baseUrl.trim()
        .trimEnd('/')
        .also { value ->
            require(value.isNotBlank()) { "FILE_BASE_URL must be configured." }
            val uri = URI(value)
            require(uri.isAbsolute && uri.host != null && uri.scheme in setOf("http", "https")) {
                "FILE_BASE_URL must be an absolute HTTP(S) URL."
            }
            require(uri.query == null && uri.fragment == null) {
                "FILE_BASE_URL must not contain a query string or fragment."
            }
        }
}


