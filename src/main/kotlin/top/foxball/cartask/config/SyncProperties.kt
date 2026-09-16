package top.foxball.cartask.config

/**
 * SyncProperties 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */

import org.springframework.boot.context.properties.ConfigurationProperties


@ConfigurationProperties(prefix = "app.sync")
data class SyncProperties(
    val taskHistoryLimit: Int = 50,
) {
    init {
        require(taskHistoryLimit >= 1) { "SYNC_TASK_HISTORY_LIMIT 必须大于或等于 1" }
    }
}


