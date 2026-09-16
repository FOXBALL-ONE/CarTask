package top.foxball.cartask.config

/**
 * AdminCredentialScrubber 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import top.foxball.cartask.repository.UserRepository
import java.nio.file.Files
import java.nio.file.StandardCopyOption


@Component
/**
 * AdminCredentialScrubber 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
class AdminCredentialScrubber(
    private val properties: AdminInitializerProperties,
    private val userRepository: UserRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    
    @Order(Ordered.LOWEST_PRECEDENCE - 49)
    @EventListener(classes = [ApplicationReadyEvent::class])
            
            
            /**
             * scrub 函数：执行与该组件职责相关的业务操作。
             * 参数和返回值遵循调用方与领域服务之间的约定。
             */
    fun scrub() {
        if (!properties.enabled || properties.forceWrite) return
        if (userRepository.findByUsername(properties.username.trim()) == null) return
        
        val path = DotenvLoader.defaultPath()
        if (!Files.exists(path)) {
            return
        }
        
        var scrubbed = false
        val rewritten = Files.readAllLines(path).map { line ->
            val trimmed = line.trimStart()
            when {
                trimmed.startsWith("$PASSWORD_KEY=") -> {
                    scrubbed = true
                    "# $PASSWORD_KEY=（管理员已创建，口令已清除；需要重置口令时把这一行改回 $PASSWORD_KEY=新口令 并重启）"
                }
                
                trimmed == "$ENABLED_KEY=true" -> {
                    scrubbed = true
                    "$ENABLED_KEY=false"
                }
                
                else -> line
            }
        }
        if (!scrubbed) return
        
        val temporary = path.resolveSibling("${path.fileName}.tmp")
        Files.writeString(temporary, rewritten.joinToString(System.lineSeparator(), postfix = System.lineSeparator()))
        Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        logger.info(
            "管理员 {} 已存在，已从 {} 清除 ADMIN_INITIALIZER_PASSWORD 并关闭 ADMIN_INITIALIZER_ENABLED",
            properties.username.trim(),
            path,
        )
    }
    
    private companion object {
        const val PASSWORD_KEY = "ADMIN_INITIALIZER_PASSWORD"
        const val ENABLED_KEY = "ADMIN_INITIALIZER_ENABLED"
    }
}


