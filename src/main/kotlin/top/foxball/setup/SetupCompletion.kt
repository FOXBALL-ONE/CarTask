package top.foxball.setup

import java.nio.file.Path
import java.time.LocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

data class CompletionResult(
    val envPath: Path,
    val backupPath: Path?,
    val completedAt: LocalDateTime,
)

/**
 * 收尾：校验步骤齐全、写出 `.env`、清掉草稿、请求重启。
 *
 * 顺序不能颠倒。先落配置再删草稿：反过来的话，写文件失败就只剩一份空草稿，实施人员得从头再填一遍。
 * 而重启放在最后，是因为它一旦触发，这套上下文马上就会关掉，后面任何一步都来不及执行。
 */
@Component
class SetupCompletion(
    private val draftStore: SetupDraftStore,
    private val envFile: SetupEnvFile,
    private val properties: SetupProperties,
    private val restartSignal: SetupRestartSignal,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun complete(frontendOrigin: String?): CompletionResult {
        val draft = draftStore.read()

        val missingSteps = SetupSection.entries.filterNot { it.id in draft.steps }
        if (missingSteps.isNotEmpty()) {
            throw SetupException("还有未完成的步骤：${missingSteps.joinToString("、") { it.title }}")
        }
        val missingValues = SetupSection.entries.filterNot { it.satisfiedBy(draft.values) }
        if (missingValues.isNotEmpty()) {
            throw SetupException("以下步骤缺少必填项：${missingValues.joinToString("、") { it.title }}")
        }

        val now = LocalDateTime.now()
        val backup = envFile.write(draft.values, frontendOrigin, now)
        draftStore.delete()
        logger.info("配置引导完成，`.env` 已就位，准备按新配置重启")
        restartSignal.request()
        return CompletionResult(envPath = properties.envPath(), backupPath = backup, completedAt = now)
    }
}
