package top.foxball.cartask.keytop

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(KeytopProperties::class)
class KeytopConfig
