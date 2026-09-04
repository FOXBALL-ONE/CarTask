package top.foxball.cartask.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.LocalDateTime

/** 启动后模拟数据写入配置，默认关闭，避免误写入非开发数据库。 */
@ConfigurationProperties(prefix = "app.mock-data")
data class MockDataProperties(
    val enabled: Boolean = false,
    /** 样例业务时间，使用 ISO-8601；未配置时使用本次启动时间。 */
    val referenceTime: LocalDateTime? = null,
)
