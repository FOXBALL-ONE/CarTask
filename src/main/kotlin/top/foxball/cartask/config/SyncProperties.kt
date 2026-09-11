package top.foxball.cartask.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** 外部数据同步任务的运行历史配置。 */
@ConfigurationProperties(prefix = "app.sync")
data class SyncProperties(
    val taskHistoryLimit: Int = 50,
) {
    init {
        require(taskHistoryLimit >= 1) { "SYNC_TASK_HISTORY_LIMIT 必须大于或等于 1" }
    }
}
