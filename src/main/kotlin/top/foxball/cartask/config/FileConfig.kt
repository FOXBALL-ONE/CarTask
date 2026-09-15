package top.foxball.cartask.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(FileProperties::class)
/** 启用文件存储配置属性，统一管理上传目录、访问地址和文件限制。 */
class FileConfig
