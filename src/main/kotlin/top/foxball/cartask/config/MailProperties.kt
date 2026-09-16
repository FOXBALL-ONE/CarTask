package top.foxball.cartask.config

/**
 * MailProperties 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */

import org.springframework.boot.context.properties.ConfigurationProperties


@ConfigurationProperties(prefix = "cartask.mail.verification")
data class MailProperties(
    val ttlSeconds: Long = 300L,
    val codeLength: Int = 6,
    val sendIntervalSeconds: Long = 60L,
    val dailyLimit: Long = 10L,
    val maxAttempts: Int = 5,
    val ipHourlyLimit: Long = 30L,
    val from: String = "",
    val subjectPrefix: String = "PELISSA",
)


