package top.itneko.keytop

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(KeytopMockProperties::class)
class KeytopApplication

fun main(args: Array<String>) {
    runApplication<KeytopApplication>(*args)
}
