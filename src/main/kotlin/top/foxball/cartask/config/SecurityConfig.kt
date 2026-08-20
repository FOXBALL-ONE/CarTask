package top.foxball.cartask.config

import jakarta.servlet.http.HttpServletResponse
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
import top.foxball.cartask.authentication.JwtAuthenticationFilter
import top.foxball.cartask.audit.AuditRequestContextFilter
import top.foxball.cartask.audit.AuditRequestContext
import top.foxball.cartask.audit.AuditAction
import top.foxball.cartask.audit.AuditCommand
import top.foxball.cartask.audit.AuditService

@Configuration
@EnableMethodSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val auditRequestContextFilter: AuditRequestContextFilter,
    private val corsProperties: CorsProperties,
    private val auditService: AuditService,
) {

    @Bean
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
                    "/error",
                ).permitAll()
                it.requestMatchers(HttpMethod.POST, "/api/auth/logout").authenticated()
                it.requestMatchers("/api/users/**").authenticated()
                it.requestMatchers(HttpMethod.GET, "/api/project/**").permitAll()
                it.requestMatchers(
                    HttpMethod.GET,
                    "/api/products/**",
                    "/api/product-types/**",
                    "/api/product-categories/**",
                    "/api/product-images/**",
                    "/api/tags/**",
                    "/api/customer-reviews/**",
                    "/api/announcements/**",
                    "/api/home/recommendations",
                ).permitAll()
                it.requestMatchers("/api/files/**").authenticated()
                it.requestMatchers(
                    HttpMethod.POST,
                    "/api/project/object-items",
                    "/api/project/object-items/*/comments",
                    "/api/project/object-items/*/join-applications",
                    "/api/project/minds",
                ).permitAll()
                it.requestMatchers(HttpMethod.POST, "/webhook").permitAll()
                it.requestMatchers(HttpMethod.POST, "/api/logistics/webhook/**").permitAll()
                it.requestMatchers(HttpMethod.GET, "/api/orders/*/shipments/**").authenticated()
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
            .addFilterBefore(auditRequestContextFilter, JwtAuthenticationFilter::class.java)
        return http.build()
    }

    private fun writeJson(response: HttpServletResponse, status: Int, message: String) {
        response.contentType = "application/json;charset=UTF-8"
        response.status = status
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store")
        response.writer.write("""{"status":$status,"message":"$message","data":{}}""")
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        corsProperties.validate()
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
            )
            maxAge = 3600
        }
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", config)
        return source
    }
}
