package top.foxball.setup

import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

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

    /**
     * probe：执行数据同步、探测或文件处理。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param root 参与本次处理的输入参数。
     * @param baseUrl 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
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
