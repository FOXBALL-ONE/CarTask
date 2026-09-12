package top.foxball.cartask.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(DashboardProperties::class)
class DashboardConfig
