package top.foxball.setup

import com.fasterxml.jackson.annotation.JsonInclude
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import top.foxball.cartask.config.DotenvLoader

/**
 * 引导草稿：向导每一步验证通过后落盘的环境变量片段。
 *
 * 落盘而不是只放在内存里，是因为向导有好几步、还可能填到一半去查凭据。浏览器的状态一刷新就没了，
 * 而重新填一遍数据库密码这件事，没有任何理由让实施人员做第二次。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SetupDraft(
    val values: Map<String, String> = emptyMap(),
    /** 已完成的步骤 id；缺哪一步，[SetupCompletion] 就不放行。 */
    val steps: List<String> = emptyList(),
)

@Component
class SetupDraftStore(
    private val properties: SetupProperties,
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val lock = Any()

    private var cached: SetupDraft? = null

    fun read(): SetupDraft = synchronized(lock) { loaded() }

    /**
     * 写入一个步骤的结果。
     *
     * 只覆盖属于该步骤的键：某一步重填时不能把别的步骤已经验证过的值一起抹掉，否则「回头改一步」
     * 会让后面所有步骤静默失效。
     */
    fun save(section: SetupSection, values: Map<String, String>) = synchronized(lock) {
        val current = loaded()
        val merged = current.values + section.slice(values)
        val drafted = SetupDraft(values = merged, steps = (current.steps + section.id).distinct())
        persist(drafted)
    }

    fun delete() = synchronized(lock) {
        cached = SetupDraft()
        Files.deleteIfExists(properties.draftPath())
        Unit
    }

    private fun loaded(): SetupDraft = cached ?: loadFromDisk().also { cached = it }

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

        // 没有草稿：从既有 `.env` 接续。重新进入引导模式（SETUP_MODE=true）改配置时，
        // 向导应当带着现有配置打开，而不是让人凭记忆把 IP、车场号、密钥再敲一遍。
        val seeded = seedFrom(DotenvLoader.read(properties.envPath()))
        if (seeded.values.isNotEmpty()) {
            logger.info("按既有 .env 预填引导草稿: 键={} 已完成步骤={}", seeded.values.keys.sorted(), seeded.steps)
        }
        return seeded
    }

    private fun persist(draft: SetupDraft) {
        val path = properties.draftPath()
        writeAtomically(path, objectMapper.writeValueAsString(draft))
        cached = draft
        // 草稿里带明文口令与密钥，权限收到「只有属主可读写」。Windows 上该调用是空操作，
        // 那里由 NTFS 的目录继承权限兜底。
        runCatching {
            Files.setPosixFilePermissions(
                path,
                java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"),
            )
        }
    }

    private fun writeAtomically(path: Path, content: String) {
        path.parent?.let { Files.createDirectories(it) }
        val temporary = path.resolveSibling("${path.fileName}.tmp")
        Files.writeString(temporary, content)
        Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    companion object {
        /**
         * 按既有配置推导草稿初值。
         *
         * 与文件读写分开，是因为这里有几个容易搞错的判断（哪些步骤算「已完成」、哪些键该继承），
         * 而它们不该靠跑一遍真文件来验证。
         */
        fun seedFrom(existing: Map<String, String>): SetupDraft {
            val inherited = existing.filterKeys { it in SetupSection.allKeys }
            // 光看 satisfiedBy 不够：短信这类「没有必填项」的步骤会被空集判成已完成，于是重新进入
            // 引导时它一步没填就已经打上勾。要求既有配置里确实出现过该步骤的键，才算这一步被配过。
            val satisfied = SetupSection.entries
                .filter { section -> section.keys.any { inherited.containsKey(it) } && section.satisfiedBy(inherited) }
                .map { it.id }
            return SetupDraft(values = inherited, steps = satisfied)
        }
    }
}
