package top.foxball.cartask.keytop

/**
 * KeytopProperties 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration
import java.time.LocalDateTime


@ConfigurationProperties(prefix = "keytop")
data class KeytopProperties(
    val baseUrl: String = "https://kp-open.keytop.cn/unite-api",
    val appId: Int = 0,
    val parkId: String = "",
    val parkName: String = "",
    val appSecret: String = "",
    val version: String = "1.0.0",
    val timeout: Duration = Duration.ofSeconds(30),
    val syncRequestInterval: Duration = Duration.ofMillis(200),
    val areaSyncCron: String = "0 0 2 * * *",
    val carCapInfoSyncCron: String = "0 */5 * * * *",
    val carCapInfoPageSize: Int = 100,
    val carCapInfoOverlapWindow: Duration = Duration.ofMinutes(30),
    val carCapInfoReconciliationWindow: Duration = Duration.ofHours(72),
    val carCapInfoReconciliationCron: String = "0 30 3 * * *",
    val carCapInfoPhotoDownloadInterval: Duration = Duration.ofMillis(200),
    val carCapInfoPhotoDownloadConcurrency: Int = 4,
    val plateSyncCron: String = "*/30 * * * * *",
    val plateReconcileCron: String = "0 0 * * * *",
    val plateSyncBatchSize: Int = 20,
    val plateSyncMaxAttempts: Int = 8,
    val plateSyncDefaultValidFrom: LocalDateTime? = null,
    val plateSyncDefaultValidTo: LocalDateTime = LocalDateTime.of(2099, 12, 31, 23, 59, 59),
    val plateSyncOperatorId: Long = 1,
    val plateSyncOperatorName: String = "carTask",
) {
    init {
        require(!carCapInfoOverlapWindow.isNegative) { "车辆进出记录同步回看时长不能为负数" }
        require(!syncRequestInterval.isNegative) { "Keytop 同步请求限频间隔不能为负数" }
        require(syncRequestInterval <= Duration.ofSeconds(60)) { "Keytop 同步请求限频间隔不能超过 60 秒" }
        require(!carCapInfoReconciliationWindow.isNegative && !carCapInfoReconciliationWindow.isZero) {
            "车辆进出记录补偿同步回看时长必须大于零"
        }
        require(!carCapInfoPhotoDownloadInterval.isNegative) { "图片下载间隔不能为负数" }
        require(carCapInfoPhotoDownloadInterval <= Duration.ofSeconds(60)) { "图片下载间隔不能超过 60 秒" }
        require(carCapInfoPhotoDownloadConcurrency in 1..16) { "图片下载并行数必须在 1 到 16 之间" }
        require(plateSyncBatchSize in 1..100) { "车牌月卡同步批量大小必须在 1 到 100 之间" }
        require(plateSyncMaxAttempts in 1..20) { "车牌月卡同步最大重试次数必须在 1 到 20 之间" }
        require(!plateSyncDefaultValidTo.isBefore(plateSyncDefaultValidFrom ?: LocalDateTime.MIN)) {
            "车牌月卡默认失效时间不能早于生效时间"
        }
        require(plateSyncOperatorId > 0) { "车牌月卡同步操作人 ID 必须大于 0" }
        require(plateSyncOperatorName.isNotBlank()) { "车牌月卡同步操作人名称不能为空" }
    }
}
