package top.foxball.cartask.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import top.foxball.cartask.logging.LoggingProperties

@Configuration
@EnableConfigurationProperties(LoggingProperties::class)
/** 启用日志配置属性，为结构化日志和操作日志执行器提供参数。 */
class LoggingConfig
