package top.foxball.cartask.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(AdminInitializerProperties::class)
/** 启用管理员初始化配置属性，集中管理系统首个管理员账号的创建参数。 */
class AdminInitializerConfig
