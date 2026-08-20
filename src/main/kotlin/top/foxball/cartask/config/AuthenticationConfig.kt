package top.foxball.cartask.config

import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import top.foxball.cartask.authentication.JwtProperties
import top.foxball.cartask.authentication.JwtAuthenticationFilter
import top.foxball.cartask.authentication.LoginRateLimitProperties
import top.foxball.cartask.audit.AuditRequestContextFilter
import java.time.Clock

@Configuration
@EnableConfigurationProperties(JwtProperties::class, LoginRateLimitProperties::class, CorsProperties::class)
class AuthenticationConfig {
    @Bean
    fun authenticationClock(): Clock = Clock.systemUTC()
    
    /** 认证过滤器只加入 Spring Security 链，禁止 Servlet 容器再次独立注册。 */
    @Bean
    fun jwtAuthenticationFilterRegistration(
        filter: JwtAuthenticationFilter,
    ): FilterRegistrationBean<JwtAuthenticationFilter> = FilterRegistrationBean(filter).apply {
        isEnabled = false
    }

    /** 审计上下文只加入 Spring Security 链，避免 Servlet 容器重复执行。 */
    @Bean
    fun auditRequestContextFilterRegistration(
        filter: AuditRequestContextFilter,
    ): FilterRegistrationBean<AuditRequestContextFilter> = FilterRegistrationBean(filter).apply {
        isEnabled = false
    }
}
