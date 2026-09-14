package top.foxball.cartask.config

import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import top.foxball.cartask.repository.UserRepository

/**
 * 管理员建好之后，把 `.env` 里的明文口令擦掉。
 *
 * 配置引导把「用户名 + 口令」写进 `.env`，交给启动期的 [AdminInitializer] 建号——这条路径复用
 * 了既有的、次序正确的建号逻辑，代价是口令会以明文留在配置文件里。而这份文件在服务器上通常要
 * 陪跑好几年，一个常年躺着的超级管理员明文口令，比引导本身带来的便利要贵得多。
 *
 * 擦除只做两件事：把 `ADMIN_INITIALIZER_PASSWORD` 那行注释掉、把 `ADMIN_INITIALIZER_ENABLED`
 * 置为 false。账号已经存在时 [AdminInitializer] 本来就会跳过写入，因此这两处改动不改变任何行为，
 * 只是让配置文件不再留着一份它已经用不上的东西。
 *
 * 只在 `force-write=false` 时动手。`force-write=true` 是「每次启动都把口令改回来」的明确要求，
 * 关掉它会把这个意图一起关掉。
 *
 * 运行次序紧跟 [AdminInitializer]：早于它会把口令在账号建好之前删掉，晚于
 * [PermissionCatalogInitializer] 则没有区别，但贴着前者更容易看出这两步是一件事。
 * @Order 必须标在方法上——ApplicationListenerMethodAdapter 只读方法上的注解。
 */
@Component
class AdminCredentialScrubber(
    private val properties: AdminInitializerProperties,
    private val userRepository: UserRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Order(Ordered.LOWEST_PRECEDENCE - 49)
    @EventListener(classes = [ApplicationReadyEvent::class])
    fun scrub() {
        if (!properties.enabled || properties.forceWrite) return
        if (userRepository.findByUsername(properties.username.trim()) == null) return

        val path = DotenvLoader.defaultPath()
        if (!Files.exists(path)) {
            // 配置来自操作系统环境变量而非 `.env`：没有可擦的文件，也不该替谁去改环境。
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
