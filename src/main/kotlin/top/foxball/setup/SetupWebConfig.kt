package top.foxball.setup

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.context.NullSecurityContextRepository
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

/**
 * 配置模式的 Web 安全：全员放行，关口交给 [SetupTokenFilter]。
 *
 * 不是「忘了加鉴权」——这套应用跑在账号体系存在之前，没有角色、没有权限码，Spring Security 在这里
 * 无凭可依。真正能防住「陌生人把系统配成自己的」是配置口令，所以这里自定义一条 `permitAll` 的过滤器链，
 * 让 Boot 的默认链（全拦截 + 生成一个内存用户）自动退让。若不走这一步，默认链会把引导接口也拦下来，
 * 表现为引导页所有请求都是 401，而日志里那行 generated password 又和引导流程对不上号。
 *
 * 跨域按最宽处理：引导阶段还不知道前端最终从哪个地址访问（本机 IP、域名、反向代理都可能），
 * 而这个阶段的接口本身只要口令对就能用。不携带 Cookie（`allowCredentials=false`），
 * 所以放开 Origin 不会顺带把浏览器的凭据一起带进来。
 */
@Configuration
class SetupWebConfig {

    @Bean
            /**
             * setupSecurityFilterChain：创建、保存或初始化相关数据。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @param http 参与本次处理的输入参数。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
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
             * setupCorsConfigurationSource：创建、保存或初始化相关数据。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
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
