package top.foxball.cartask.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** 浏览器跨域访问白名单；生产环境通过环境变量覆盖本地开发 Origin。 */
@ConfigurationProperties(prefix = "shopmall.security.cors")
data class CorsProperties(
    val allowedOrigins: List<String> = emptyList(),
    val allowedOriginPatterns: List<String> = emptyList(),
    val allowCredentials: Boolean = true,
) {
    fun origins(): List<String> = allowedOrigins.map(String::trim).filter(String::isNotEmpty).distinct()
    
    fun originPatterns(): List<String> = allowedOriginPatterns.map(String::trim).filter(String::isNotEmpty).distinct()
    
    fun validate() {
        if (allowCredentials) {
            require("*" !in origins() && "*" !in originPatterns()) {
                "CORS 允许凭据时不能使用全局通配 Origin"
            }
        }
    }
}
