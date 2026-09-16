package top.foxball.cartask.config

/**
 * LoggingConfig 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import top.foxball.cartask.logging.LoggingProperties

@Configuration
@EnableConfigurationProperties(LoggingProperties::class)

/**
 * LoggingConfig 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
class LoggingConfig


