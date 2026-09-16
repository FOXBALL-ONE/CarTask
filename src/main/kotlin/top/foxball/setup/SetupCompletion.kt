package top.foxball.setup

/**
 * SetupCompletion 配置引导组件说明。
 *
 * 该文件负责系统初始化向导中的配置探测、持久化、校验或 Web 入口逻辑。
 */
/**
 * SetupCompletion 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.nio.file.Path
import java.time.LocalDateTime

/**
 * CompletionResult 的职责与行为说明。
 * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
 */
data class CompletionResult(
    val envPath: Path,
    val backupPath: Path?,
    val completedAt: LocalDateTime,
)


@Component
/**
 * SetupCompletion 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
/**
 * SetupCompletion 的职责与行为说明。
 * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
 */
class SetupCompletion(
    private val draftStore: SetupDraftStore,
    private val envFile: SetupEnvFile,
    private val properties: SetupProperties,
    private val restartSignal: SetupRestartSignal,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    
    
    /**
     * complete 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    /**
     * complete 的职责与行为说明。
     * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
     */
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





