package top.foxball.cartask.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(MockDataProperties::class)
/** 启用模拟数据配置属性，控制开发或测试环境中的样例数据初始化。 */
class MockDataConfig
