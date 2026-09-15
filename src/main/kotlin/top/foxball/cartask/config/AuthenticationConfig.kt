package top.foxball.cartask.config

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
class AuthenticationConfig {
    @Bean
            /**
             * authenticationClock：完成身份认证、令牌或验证码处理。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
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
