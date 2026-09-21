package top.itneko.keytop

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "keytop")
data class KeytopMockProperties(
    val appId: Int = 12250,
    val parkId: String = "591007282",
    val appSecret: String = "secret",
    val version: String = "1.0.0",
)
