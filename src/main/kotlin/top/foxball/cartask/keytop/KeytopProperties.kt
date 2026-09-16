package top.foxball.cartask.keytop

/**
 * KeytopProperties 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration


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
    val carCapInfoOverlapWindow: Duration = Duration.ofMinutes(30),
    val carCapInfoReconciliationWindow: Duration = Duration.ofHours(72),
    val carCapInfoReconciliationCron: String = "0 30 3 * * *",
) {
    init {
        require(!carCapInfoOverlapWindow.isNegative) { "车辆进出记录同步回看时长不能为负数" }
        require(!carCapInfoReconciliationWindow.isNegative && !carCapInfoReconciliationWindow.isZero) {
            "车辆进出记录补偿同步回看时长必须大于零"
        }
    }
}


