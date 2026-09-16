package top.foxball.cartask.config

/**
 * DashboardProperties 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */

import org.springframework.boot.context.properties.ConfigurationProperties


@ConfigurationProperties(prefix = "app.dashboard")
data class DashboardProperties(
    val zoneCodes: List<String> = listOf("1", "2"),
) {
    init {
        require(zoneCodes.isNotEmpty()) { "DASHBOARD_ZONE_CODES 必须至少配置一个区域编码" }
        require(zoneCodes.all { it.isNotBlank() }) { "DASHBOARD_ZONE_CODES 不能包含空编码" }
    }
}


