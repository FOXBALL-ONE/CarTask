package top.foxball.setup

/**
 * StorageProbe 配置引导组件说明。
 *
 * 该文件负责系统初始化向导中的配置探测、持久化、校验或 Web 入口逻辑。
 */
/**
 * StorageProbe 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */

import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

/**
 * StorageProbeResult 的职责与行为说明。
 * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
 */
data class StorageProbeResult(
    val root: Path,
    val baseUrl: String,
)


@Component
/**
 * StorageProbe 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
/**
 * StorageProbe 的职责与行为说明。
 * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
 */
class StorageProbe {
    
    
    /**
     * probe 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    /**
     * probe 的职责与行为说明。
     * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
     */
    fun probe(root: String, baseUrl: String): StorageProbeResult {
        val trimmedBaseUrl = baseUrl.trim().trimEnd('/')
        if (!trimmedBaseUrl.startsWith("http://") && !trimmedBaseUrl.startsWith("https://")) {
            throw SetupException("下载基址必须是绝对 HTTP(S) 地址，例如 http://192.168.1.95:8080")
        }
        
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





