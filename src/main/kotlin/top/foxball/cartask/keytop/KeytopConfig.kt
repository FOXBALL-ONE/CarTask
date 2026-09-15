package top.foxball.cartask.keytop

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(KeytopProperties::class)
/** 启用科拓开放平台配置属性，供客户端和同步任务读取连接参数。 */
class KeytopConfig
