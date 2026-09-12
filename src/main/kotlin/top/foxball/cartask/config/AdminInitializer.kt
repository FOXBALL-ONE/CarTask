package top.foxball.cartask.config

import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import top.foxball.cartask.entity.Role
import top.foxball.cartask.entity.User
import top.foxball.cartask.repository.RoleRepository
import top.foxball.cartask.repository.UserRepository
import java.time.LocalDateTime

/**
 * 按环境变量配置，在启动完成后创建或强制更新一个管理员账号。
 *
 * 运行顺序固定为早于 [PermissionCatalogInitializer]：后者只在角色行已存在时才按角色填充权限，
 * 若它先跑完再建角色行，这个管理员会带着一个零权限的角色登录，表现为登录后什么都看不到。
 * @Order 必须标在方法上——ApplicationListenerMethodAdapter 只读方法上的注解，标在类上会被忽略。
 */
@Component
class AdminInitializer(
    private val properties: AdminInitializerProperties,
    private val passwordEncoder: PasswordEncoder,
    private val roleRepository: RoleRepository,
    private val userRepository: UserRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Order(Ordered.LOWEST_PRECEDENCE - 50)
    @EventListener(classes = [ApplicationReadyEvent::class])
    @Transactional
    fun write() {
        if (!properties.enabled) return
        properties.validate()

        val adminRole = roleRepository.findByNameIgnoreCase(ADMIN_ROLE)
            ?: roleRepository.save(
                Role().apply {
                    name = ADMIN_ROLE
                    description = "管理员"
                    enabled = true
                },
            )
        val existing = userRepository.findByUsername(properties.username.trim())
        if (existing == null) {
            val now = LocalDateTime.now()
            userRepository.save(
                User().apply {
                    username = properties.username.trim()
                    nickName = "管理员"
                    email = "${properties.username.trim()}@local.invalid"
                    passwordHash = passwordEncoder.encode(properties.password).toString()
                    role = ADMIN_ROLE
                    roles = linkedSetOf(adminRole)
                    status = User.Status.Activity
                    enabled = true
                    createdAt = now
                    updatedAt = now
                },
            )
            logger.info("管理员初始化完成: username={}", properties.username.trim())
            return
        }
        if (!properties.forceWrite) {
            logger.info("管理员已存在，跳过初始化: username={}", properties.username.trim())
            return
        }

        existing.passwordHash = passwordEncoder.encode(properties.password).toString()
        existing.role = ADMIN_ROLE
        existing.roles = linkedSetOf(adminRole)
        existing.status = User.Status.Activity
        existing.enabled = true
        existing.updatedAt = LocalDateTime.now()
        userRepository.save(existing)
        logger.info("管理员初始化强制写入完成: username={}", properties.username.trim())
    }

    private companion object {
        const val ADMIN_ROLE = "ADMIN"
    }
}
