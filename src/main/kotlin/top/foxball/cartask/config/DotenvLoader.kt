package top.foxball.cartask.config

/**
 * DotenvLoader 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */

import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource
import java.nio.file.Files
import java.nio.file.Path


/**
 * DotenvLoader 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
object DotenvLoader {
    
    const val ENV_FILE_VARIABLE = "APP_ENV_FILE"
    
    private const val DEFAULT_ENV_FILE = ".env"
    
    
    /**
     * defaultPath 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun defaultPath(): Path {
        val configured = System.getProperty(ENV_FILE_VARIABLE)?.takeIf { it.isNotBlank() }
            ?: System.getenv(ENV_FILE_VARIABLE)?.takeIf { it.isNotBlank() }
        return Path.of(configured ?: DEFAULT_ENV_FILE).toAbsolutePath().normalize()
    }
    
    
    /**
     * read 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun read(path: Path): Map<String, String> {
        if (!Files.exists(path)) return emptyMap()
        return Files.readAllLines(path).asSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { if (it.startsWith("export ")) it.removePrefix("export ").trim() else it }
            .mapNotNull { line ->
                val index = line.indexOf('=')
                if (index <= 0) return@mapNotNull null
                val key = line.substring(0, index).trim()
                val raw = line.substring(index + 1).trim()
                val value = if (raw.length >= 2 && raw.first() == '"' && raw.last() == '"') raw.substring(
                    1,
                    raw.length - 1
                ) else raw
                key to value
            }.toMap()
    }
    
    
    /**
     * addTo 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun addTo(environment: ConfigurableEnvironment, path: Path) {
        val values = read(path)
        if (values.isNotEmpty()) environment.propertySources.addLast(MapPropertySource("dotenv", values))
    }
}


