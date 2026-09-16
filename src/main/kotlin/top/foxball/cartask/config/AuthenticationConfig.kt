package top.foxball.cartask.config

/**
 * AuthenticationConfig 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import top.foxball.cartask.audit.AuditRequestContextFilter
import top.foxball.cartask.authentication.JwtAuthenticationFilter
import top.foxball.cartask.authentication.JwtProperties
import top.foxball.cartask.authentication.LoginRateLimitProperties
import java.time.Clock

@Configuration
@EnableConfigurationProperties(JwtProperties::class, LoginRateLimitProperties::class, CorsProperties::class)
/**
 * AuthenticationConfig 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
class AuthenticationConfig {
    @Bean
            
            
            /**
             * authenticationClock 函数：执行与该组件职责相关的业务操作。
             * 参数和返回值遵循调用方与领域服务之间的约定。
             */
    fun authenticationClock(): Clock = Clock.systemUTC()
    
    
    @Bean
            /**
             * jwtAuthenticationFilterRegistration 函数：执行与该组件职责相关的业务操作。
             * 参数和返回值遵循调用方与领域服务之间的约定。
             */
    fun jwtAuthenticationFilterRegistration(
        filter: JwtAuthenticationFilter,
    ): FilterRegistrationBean<JwtAuthenticationFilter> = FilterRegistrationBean(filter).apply {
        isEnabled = false
    }
    
    
    @Bean
            /**
             * auditRequestContextFilterRegistration 函数：执行与该组件职责相关的业务操作。
             * 参数和返回值遵循调用方与领域服务之间的约定。
             */
    fun auditRequestContextFilterRegistration(
        filter: AuditRequestContextFilter,
    ): FilterRegistrationBean<AuditRequestContextFilter> = FilterRegistrationBean(filter).apply {
        isEnabled = false
    }
}


