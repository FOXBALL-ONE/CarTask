package top.foxball.cartask.config

/**
 * SecurityEncoderConfig 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder


@Configuration
@EnableConfigurationProperties(Argon2PasswordEncoderProperties::class)
/**
 * SecurityEncoderConfig 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
class SecurityEncoderConfig(
    private val properties: Argon2PasswordEncoderProperties,
) {
    @Bean
            
            
            /**
             * passwordEncoder 函数：执行与该组件职责相关的业务操作。
             * 参数和返回值遵循调用方与领域服务之间的约定。
             */
    fun passwordEncoder(): PasswordEncoder = Argon2PasswordEncoder(
        properties.saltLength,
        properties.hashLength,
        properties.parallelism,
        properties.memory,
        properties.iterations,
    )
}


@ConfigurationProperties(prefix = "cartask.security.password.argon2")
data class Argon2PasswordEncoderProperties(
    val saltLength: Int = 16,
    val hashLength: Int = 32,
    val parallelism: Int = 1,
    val memory: Int = 16_384,
    val iterations: Int = 2,
)


