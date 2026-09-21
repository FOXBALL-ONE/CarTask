package top.itneko.keytop

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(KeytopMockProperties::class)
class KeytopMockConfig
