package top.foxball.cartask.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(SyncProperties::class)
/** 启用同步任务配置属性，为外部平台数据同步提供统一配置入口。 */
class SyncConfig
