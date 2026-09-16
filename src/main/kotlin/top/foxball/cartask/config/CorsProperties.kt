package top.foxball.cartask.config

/**
 * CorsProperties 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */

import org.springframework.boot.context.properties.ConfigurationProperties


@ConfigurationProperties(prefix = "cartask.security.cors")
data class CorsProperties(
    val allowedOrigins: List<String> = emptyList(),
    val allowedOriginPatterns: List<String> = emptyList(),
    val allowCredentials: Boolean = true,
) {
    
    
    /**
     * origins 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun origins(): List<String> = allowedOrigins.map(String::trim).filter(String::isNotEmpty).distinct()
    
    
    /**
     * originPatterns 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun originPatterns(): List<String> = allowedOriginPatterns.map(String::trim).filter(String::isNotEmpty).distinct()
    
    
    /**
     * validate 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun validate() {
        if (allowCredentials) {
            require("*" !in origins() && "*" !in originPatterns()) {
                "CORS 允许凭据时不能使用全局通配 Origin"
            }
        }
    }
}


