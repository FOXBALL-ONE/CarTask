package top.foxball.cartask.config

/**
 * DefaultAdminProperties 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */

import org.springframework.boot.context.properties.ConfigurationProperties


@ConfigurationProperties(prefix = "cartask.security.jwt.dev.default-admin")
data class DefaultAdminProperties(
    val username: String = "admin",
    val password: String = "admin",
    val email: String = "admin",
)


