package top.foxball.cartask.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(DashboardProperties::class)
/** 启用仪表盘配置属性，为首页统计和展示逻辑提供统一参数来源。 */
class DashboardConfig
