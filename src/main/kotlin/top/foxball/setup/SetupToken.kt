package top.foxball.setup

/**
 * SetupToken 配置引导组件说明。
 *
 * 该文件负责系统初始化向导中的配置探测、持久化、校验或 Web 入口逻辑。
 */
/**
 * SetupToken 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */

import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom


@Component
/**
 * SetupToken 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
/**
 * SetupToken 的职责与行为说明。
 * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
 */
class SetupToken(properties: SetupProperties) {
    
    val fromConfiguration: Boolean = properties.token.trim().isNotEmpty()
    
    private val value: String = properties.token.trim().ifEmpty { randomValue() }
    
    
    /**
     * value 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    /**
     * value 的职责与行为说明。
     * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
     */
    fun value(): String = value
    
    
    /**
     * matches 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    /**
     * matches 的职责与行为说明。
     * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
     */
    fun matches(candidate: String?): Boolean {
        val provided = candidate?.trim().orEmpty()
        return provided.isNotEmpty() && MessageDigest.isEqual(
            provided.toByteArray(StandardCharsets.UTF_8),
            value.toByteArray(StandardCharsets.UTF_8),
        )
    }
    
    
    /**
     * randomValue 的职责与行为说明。
     * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
     */
    private fun randomValue(): String {
        val random = SecureRandom()
        return (1..LENGTH)
            .map { ALPHABET[random.nextInt(ALPHABET.length)] }
            .joinToString("")
    }
    
    private companion object {
        const val LENGTH = 8
        const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    }
}





