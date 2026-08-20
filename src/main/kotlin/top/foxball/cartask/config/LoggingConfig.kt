package top.foxball.cartask.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import top.foxball.cartask.logging.LoggingProperties

@Configuration
@EnableConfigurationProperties(LoggingProperties::class)
class LoggingConfig
