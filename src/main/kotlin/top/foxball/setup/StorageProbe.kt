package top.foxball.setup

import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import org.springframework.stereotype.Component

data class StorageProbeResult(
    val root: Path,
    val baseUrl: String,
)

/**
 * 文件存储配置探测。
 *
 * 目录只填不验是不够的：`FILE_STORAGE_ROOT` 指向一个服务进程没有写权限的路径（Windows 上的
 * `D:\` 根目录、Linux 上挂在只读卷里的目录）时，服务能正常启动、能登录、能查询，直到有人上传
 * 第一个附件才报错，而那时已经离配置现场很远了。这里就地写一个探针文件再删掉，把问题留在向导里。
 *
 * 下载基址必须当场校验是绝对 HTTP(S) 地址：它是拼给浏览器用的，填成相对路径或内网别名时
 * 服务端一切正常，只有前端下载会失败。
 */
@Component
class StorageProbe {

    fun probe(root: String, baseUrl: String): StorageProbeResult {
        val trimmedBaseUrl = baseUrl.trim().trimEnd('/')
        if (!trimmedBaseUrl.startsWith("http://") && !trimmedBaseUrl.startsWith("https://")) {
            throw SetupException("下载基址必须是绝对 HTTP(S) 地址，例如 http://192.168.1.95:8080")
        }

        // 留空表示用服务的工作目录，与 application.yaml 的 `${FILE_STORAGE_ROOT:${user.dir}}` 一致。
        val directory = root.trim().takeIf { it.isNotEmpty() }
            ?.let { Path.of(it).toAbsolutePath().normalize() }
            ?: Path.of("").toAbsolutePath().normalize()

        val probeFile = directory.resolve(".setup-write-probe-${UUID.randomUUID()}")
        try {
            Files.createDirectories(directory)
            Files.writeString(probeFile, "setup")
        } catch (exception: Exception) {
            throw SetupException("存储目录不可写：${directory}。${exception.message.orEmpty()}", exception)
        } finally {
            runCatching { Files.deleteIfExists(probeFile) }
        }
        return StorageProbeResult(root = directory, baseUrl = trimmedBaseUrl)
    }
}
