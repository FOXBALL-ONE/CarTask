package top.foxball.cartask.config

/**
 * AdminInitializerProperties 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */

import org.springframework.boot.context.properties.ConfigurationProperties


@ConfigurationProperties(prefix = "app.admin-initializer")
data class AdminInitializerProperties(
    val enabled: Boolean = false,
    val username: String = "admin",
    val password: String = "",
    val forceWrite: Boolean = false,
) {
    
    
    /**
     * validate 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun validate() {
        require(username.isNotBlank()) { "ADMIN_INITIALIZER_USERNAME 不能为空" }
        require(password.isNotBlank()) { "ADMIN_INITIALIZER_PASSWORD 不能为空" }
    }
}


