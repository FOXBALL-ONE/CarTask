package top.foxball.cartask.config

/**
 * AdminInitializer 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */

import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import top.foxball.cartask.authentication.SecurityRole
import top.foxball.cartask.entity.Role
import top.foxball.cartask.entity.User
import top.foxball.cartask.repository.RoleRepository
import top.foxball.cartask.repository.UserRepository
import java.time.LocalDateTime


@Component
/**
 * AdminInitializer 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
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
            
            
            /**
             * write 函数：执行与该组件职责相关的业务操作。
             * 参数和返回值遵循调用方与领域服务之间的约定。
             */
    fun write() {
        if (!properties.enabled) return
        properties.validate()
        
        val adminRole = roleRepository.findByNameIgnoreCase(SecurityRole.SUPER_ADMIN)
            ?: roleRepository.save(
                Role().apply {
                    name = SecurityRole.SUPER_ADMIN
                    description = "超级管理员"
                    enabled = true
                },
            )
        val username = properties.username.trim()
        val existing = userRepository.findByUsername(username)
        if (existing == null) {
            val now = LocalDateTime.now()
            userRepository.save(
                User().apply {
                    this.username = username
                    nickName = "管理员"
                    email = "${username}@local.invalid"
                    passwordHash = passwordEncoder.encode(properties.password).toString()
                    role = SecurityRole.SUPER_ADMIN
                    roles = linkedSetOf(adminRole)
                    status = User.Status.Activity
                    enabled = true
                    createdAt = now
                    updatedAt = now
                },
            )
            logger.info("管理员初始化完成: username={}", username)
            return
        }
        
        val roleAligned = existing.role != SecurityRole.SUPER_ADMIN
        if (roleAligned) {
            existing.role = SecurityRole.SUPER_ADMIN
            existing.roles = linkedSetOf(adminRole)
        }
        if (!properties.forceWrite) {
            if (!roleAligned) {
                logger.info("管理员已存在，跳过初始化: username={}", username)
                return
            }
            existing.updatedAt = LocalDateTime.now()
            userRepository.save(existing)
            logger.info("管理员角色对齐为超级管理员: username={}", username)
            return
        }
        
        existing.passwordHash = passwordEncoder.encode(properties.password).toString()
        existing.status = User.Status.Activity
        existing.enabled = true
        existing.updatedAt = LocalDateTime.now()
        userRepository.save(existing)
        logger.info("管理员初始化强制写入完成: username={}", username)
    }
}


