package top.foxball.cartask.config

import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.context.NullSecurityContextRepository
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import top.foxball.cartask.audit.*
import top.foxball.cartask.authentication.JwtAuthenticationFilter
import top.foxball.cartask.authentication.PasswordChangeRequiredFilter

@Configuration
@EnableMethodSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val passwordChangeRequiredFilter: PasswordChangeRequiredFilter,
    private val auditRequestContextFilter: AuditRequestContextFilter,
    private val corsProperties: CorsProperties,
    private val auditService: AuditService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
            /**
             * securityFilterChain：执行当前模块中的业务操作。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @param http 参与本次处理的输入参数。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors(Customizer.withDefaults())
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .securityContext { it.securityContextRepository(NullSecurityContextRepository()) }
            .requestCache { it.disable() }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .authorizeHttpRequests {
                it.requestMatchers(
                    "/api/auth/login",
                    "/api/auth/captcha",
                    "/api/auth/sms/**",
                    // 前端要在拿到 token 之前问一句「系统配置好了没有」，据此决定跳引导页还是登录页。
                    "/api/setup/status",
                    "/error",
                ).permitAll()
                it.requestMatchers(HttpMethod.POST, "/api/auth/logout").authenticated()
                it.requestMatchers("/api/users/**").authenticated()
                it.requestMatchers(HttpMethod.GET, "/api/project/**").permitAll()
                it.requestMatchers(
                    HttpMethod.GET,
                    "/api/tags/**",
                    "/api/customer-reviews/**",
                    "/api/announcements/**",
                    "/api/home/recommendations",
                ).permitAll()
                it.requestMatchers("/api/files/**").authenticated()
                it.requestMatchers(HttpMethod.POST, "/api/logistics/webhook/**").permitAll()
                it.requestMatchers("/admin/api/**").authenticated()
                it.requestMatchers("/actuator/health", "/actuator/info").permitAll()
                it.anyRequest().authenticated()
            }
            .exceptionHandling {
                it.authenticationEntryPoint { _, response, _ ->
                    response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                    writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized")
                }
                it.accessDeniedHandler { request, response, accessDenied ->
                    runCatching {
                        auditService.record(
                            AuditCommand(
                                AuditAction.AUTHORIZATION_DENIED,
                                "http_request",
                                request.requestURI,
                                result = top.foxball.cartask.entity.AuditEvent.Result.DENIED,
                                reasonCode = "ACCESS_DENIED",
                                reason = accessDenied.message,
                                targetSummary = mapOf("method" to request.method, "path" to request.requestURI),
                                idempotencyKey = AuditRequestContext.current()?.requestId?.let { "denied:$it:${request.method}:${request.requestURI}" },
                            ),
                        )
                    }
                    response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                    writeJson(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden")
                }
            }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterAfter(passwordChangeRequiredFilter, JwtAuthenticationFilter::class.java)
            .addFilterBefore(auditRequestContextFilter, JwtAuthenticationFilter::class.java)
        return http.build()
    }

    /**
     * writeJson：创建、保存或初始化相关数据。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param response 参与本次处理的输入参数。
     * @param status 参与本次处理的输入参数。
     * @param message 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    private fun writeJson(response: HttpServletResponse, status: Int, message: String) {
        response.contentType = "application/json;charset=UTF-8"
        response.status = status
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store")
        response.writer.write("""{"status":$status,"success":${status in 200..299},"message":"$message"}""")
    }

    @Bean
            /**
             * corsConfigurationSource：执行当前模块中的业务操作。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun corsConfigurationSource(): CorsConfigurationSource {
        corsProperties.validate()
        if ("*" in corsProperties.origins() || "*" in corsProperties.originPatterns()) {
            logger.warn(
                "CORS 已对任意来源开放（cartask.security.cors.allowed-origin-patterns=*）：联调完请收回前端白名单",
            )
        }
        val config = CorsConfiguration().apply {
            allowedOrigins = corsProperties.origins()
            allowedOriginPatterns = corsProperties.originPatterns()
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf(
                "Authorization",
                "Content-Type",
                "Accept",
                "X-Request-Id",
                "X-Requested-With",
                "Idempotency-Key",
            )
            allowCredentials = corsProperties.allowCredentials
            exposedHeaders = listOf(
                "Authorization",
                "Retry-After",
                "X-RateLimit-Limit",
                "X-RateLimit-Remaining",
                "X-Request-Id",
                // 不暴露它，浏览器读不到导出文件名，Excel 下载只能退化成前端拼的默认名。
                "Content-Disposition",
            )
            maxAge = 3600
        }
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", config)
        return source
    }
}
