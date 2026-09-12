package top.foxball.cartask.keytop

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * 科拓开放平台连接配置。
 *
 * [baseUrl] 可以切换生产和测试环境；[appId]、[parkId] 和 [appSecret] 由科拓平台分配。
 * 科拓接口不返回车场名称，[parkName] 用于本地保存停车场详情时的显示名称。
 */
@ConfigurationProperties(prefix = "keytop")
data class KeytopProperties(
    val baseUrl: String = "https://kp-open.keytop.cn/unite-api",
    val appId: Int = 0,
    val parkId: String = "",
    val parkName: String = "",
    val appSecret: String = "",
    val version: String = "1.0.0",
    val timeout: Duration = Duration.ofSeconds(30),
    val areaSyncCron: String = "0 0 2 * * *",
    val carCapInfoSyncCron: String = "0 */5 * * * *",
    val carCapInfoPageSize: Int = 100,
    /** 增量同步的回看时长，用于覆盖上游延迟入库和短暂排序变化。 */
    val carCapInfoOverlapWindow: Duration = Duration.ofMinutes(30),
    /** 定期补偿同步的回看时长。 */
    val carCapInfoReconciliationWindow: Duration = Duration.ofHours(72),
    /** 定期补偿同步的 cron 表达式。 */
    val carCapInfoReconciliationCron: String = "0 30 3 * * *",
) {
    init {
        require(!carCapInfoOverlapWindow.isNegative) { "车辆进出记录同步回看时长不能为负数" }
        require(!carCapInfoReconciliationWindow.isNegative && !carCapInfoReconciliationWindow.isZero) {
            "车辆进出记录补偿同步回看时长必须大于零"
        }
    }
}
