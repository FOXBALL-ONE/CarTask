package top.foxball.cartask.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

/** 注册全局 Argon2 密码编码器。 */
@Configuration
@EnableConfigurationProperties(Argon2PasswordEncoderProperties::class)
class SecurityEncoderConfig(
    private val properties: Argon2PasswordEncoderProperties,
) {
    @Bean
            /**
             * passwordEncoder：执行当前模块中的业务操作。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun passwordEncoder(): PasswordEncoder = Argon2PasswordEncoder(
        properties.saltLength,
        properties.hashLength,
        properties.parallelism,
        properties.memory,
        properties.iterations,
    )
}

/** Argon2 参数；调整时须兼顾登录延迟、内存预算与已有密码哈希的兼容性。 */
@ConfigurationProperties(prefix = "cartask.security.password.argon2")
data class Argon2PasswordEncoderProperties(
    val saltLength: Int = 16,
    val hashLength: Int = 32,
    val parallelism: Int = 1,
    val memory: Int = 16_384,
    val iterations: Int = 2,
)
