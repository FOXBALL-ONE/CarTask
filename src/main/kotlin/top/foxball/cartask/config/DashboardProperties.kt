package top.foxball.cartask.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 首页仪表盘统计的范围配置。
 *
 * 仪表盘的车位指标按**车场/区域编码**统计，而不是全库汇总：车场实际容量来自科拓同步的区域
 * 车位数，一个库里有多个车场时全库汇总没有业务含义。
 */
@ConfigurationProperties(prefix = "app.dashboard")
data class DashboardProperties(
    /** 仪表盘统计的车场/区域编码，对应 [top.foxball.cartask.entity.type.ZoneType.zoneCode]。 */
    val zoneCodes: List<String> = listOf("1", "2"),
) {
    init {
        require(zoneCodes.isNotEmpty()) { "DASHBOARD_ZONE_CODES 必须至少配置一个区域编码" }
        require(zoneCodes.all { it.isNotBlank() }) { "DASHBOARD_ZONE_CODES 不能包含空编码" }
    }
}
