package top.foxball.setup

/**
 * SetupWebConfig 配置引导组件说明。
 *
 * 该文件负责系统初始化向导中的配置探测、持久化、校验或 Web 入口逻辑。
 */

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.context.NullSecurityContextRepository
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource


@Configuration
/**
 * SetupWebConfig 的职责与行为说明。
 * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
 */
class SetupWebConfig {
    
    @Bean
            
            
            /**
             * setupSecurityFilterChain 的职责与行为说明。
             * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
             */
    fun setupSecurityFilterChain(http: HttpSecurity): SecurityFilterChain = http
        .csrf { it.disable() }
        .cors { it.configurationSource(setupCorsConfigurationSource()) }
        .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        .securityContext { it.securityContextRepository(NullSecurityContextRepository()) }
        .requestCache { it.disable() }
        .formLogin { it.disable() }
        .httpBasic { it.disable() }
        .authorizeHttpRequests { it.anyRequest().permitAll() }
        .build()
    
    @Bean
            
            
            /**
             * setupCorsConfigurationSource 的职责与行为说明。
             * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
             */
    fun setupCorsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration().apply {
            allowedOriginPatterns = listOf("*")
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf(
                "Accept",
                "Content-Type",
                "X-Request-Id",
                SetupTokenFilter.HEADER,
            )
            allowCredentials = false
            maxAge = 3600
        }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", configuration)
        }
    }
}



