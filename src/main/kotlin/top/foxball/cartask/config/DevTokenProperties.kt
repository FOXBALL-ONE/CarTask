package top.foxball.cartask.config

/**
 * DevTokenProperties 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */

import org.springframework.boot.context.properties.ConfigurationProperties


@ConfigurationProperties(prefix = "cartask.security.jwt.dev.fixed-token")
data class DevTokenProperties(
    val enabled: Boolean = false,
    val jti: String = "00000000-0000-0000-0000-000000000000",
    val ttlSeconds: Long = 31_536_000L,
)


