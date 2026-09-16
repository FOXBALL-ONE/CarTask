package top.foxball.setup

/**
 * SetupDraftStore 配置引导组件说明。
 *
 * 该文件负责系统初始化向导中的配置探测、持久化、校验或 Web 入口逻辑。
 */
/**
 * SetupDraftStore 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */

import com.fasterxml.jackson.annotation.JsonInclude
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import top.foxball.cartask.config.DotenvLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption


@JsonInclude(JsonInclude.Include.NON_NULL)
/**
 * SetupDraft 的职责与行为说明。
 * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
 */
data class SetupDraft(
    val values: Map<String, String> = emptyMap(),
    
    val steps: List<String> = emptyList(),
)

@Component
/**
 * SetupDraftStore 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
/**
 * SetupDraftStore 的职责与行为说明。
 * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
 */
class SetupDraftStore(
    private val properties: SetupProperties,
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val lock = Any()
    
    private var cached: SetupDraft? = null
    
    
    /**
     * read 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    /**
     * read 的职责与行为说明。
     * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
     */
    fun read(): SetupDraft = synchronized(lock) { loaded() }
    
    
    /**
     * save 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    /**
     * save 的职责与行为说明。
     * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
     */
    fun save(section: SetupSection, values: Map<String, String>) = synchronized(lock) {
        val current = loaded()
        val merged = current.values + section.slice(values)
        val drafted = SetupDraft(values = merged, steps = (current.steps + section.id).distinct())
        persist(drafted)
    }
    
    
    /**
     * delete 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    /**
     * delete 的职责与行为说明。
     * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
     */
    fun delete() = synchronized(lock) {
        cached = SetupDraft()
        Files.deleteIfExists(properties.draftPath())
        Unit
    }
    
    
    /**
     * loaded 的职责与行为说明。
     * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
     */
    private fun loaded(): SetupDraft = cached ?: loadFromDisk().also { cached = it }
    
    
    /**
     * loadFromDisk 的职责与行为说明。
     * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
     */
    private fun loadFromDisk(): SetupDraft {
        val path = properties.draftPath()
        val fromDisk = if (Files.exists(path)) {
            runCatching { objectMapper.readValue(Files.readString(path), SetupDraft::class.java) }
                .onFailure { logger.warn("引导草稿无法解析，按空草稿继续: {}", path, it) }
                .getOrNull()
        } else {
            null
        }
        if (fromDisk != null) return fromDisk
        
        val seeded = seedFrom(DotenvLoader.read(properties.envPath()))
        if (seeded.values.isNotEmpty()) {
            logger.info("按既有 .env 预填引导草稿: 键={} 已完成步骤={}", seeded.values.keys.sorted(), seeded.steps)
        }
        return seeded
    }
    
    
    /**
     * persist 的职责与行为说明。
     * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
     */
    private fun persist(draft: SetupDraft) {
        val path = properties.draftPath()
        writeAtomically(path, objectMapper.writeValueAsString(draft))
        cached = draft
        runCatching {
            Files.setPosixFilePermissions(
                path,
                java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"),
            )
        }
    }
    
    
    /**
     * writeAtomically 的职责与行为说明。
     * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
     */
    private fun writeAtomically(path: Path, content: String) {
        path.parent?.let { Files.createDirectories(it) }
        val temporary = path.resolveSibling("${path.fileName}.tmp")
        Files.writeString(temporary, content)
        Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
    
    companion object {
        
        
        /**
         * seedFrom 函数：执行与该组件职责相关的业务操作。
         * 参数和返回值遵循调用方与领域服务之间的约定。
         */
        /**
         * seedFrom 的职责与行为说明。
         * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
         */
        fun seedFrom(existing: Map<String, String>): SetupDraft {
            val inherited = existing.filterKeys { it in SetupSection.allKeys }
            val satisfied = SetupSection.entries
                .filter { section -> section.keys.any { inherited.containsKey(it) } && section.satisfiedBy(inherited) }
                .map { it.id }
            return SetupDraft(values = inherited, steps = satisfied)
        }
    }
}





