package top.foxball.cartask.config

/**
 * MockDataProperties 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.LocalDateTime


@ConfigurationProperties(prefix = "app.mock-data")
data class MockDataProperties(
    val enabled: Boolean = false,
    val referenceTime: LocalDateTime? = null,
)


