package top.foxball.cartask.config

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

/**
 * 按环境变量配置，在启动完成后创建或强制更新一个管理员账号。
 *
 * 这个账号是库的引导账号，角色必须是 [SecurityRole.SUPER_ADMIN]：`role:manage`、
 * `permission:manage`、`user:role-assign` 这些治理权限只有超级管理员拿得到，建成平台管理
 * （ADMIN）的话，全新库里没有任何人能给自己或别人提权，只能改库。
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
            /**
             * write：创建、保存或初始化相关数据。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
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

        // 角色无条件对齐：它是这个初始化器自身的契约，不是需要保留的人工配置。上一版把默认
        // 管理员建成了平台管理（ADMIN），只有新建时才写角色的话，存量库重启一次也修不回来。
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
