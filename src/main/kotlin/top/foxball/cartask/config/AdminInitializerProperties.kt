package top.foxball.cartask.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** 启动期管理员初始化配置，真实值通过环境变量注入。 */
@ConfigurationProperties(prefix = "app.admin-initializer")
data class AdminInitializerProperties(
    val enabled: Boolean = false,
    val username: String = "admin",
    val password: String = "",
    val forceWrite: Boolean = false,
) {
    fun validate() {
        require(username.isNotBlank()) { "ADMIN_INITIALIZER_USERNAME 不能为空" }
        require(password.isNotBlank()) { "ADMIN_INITIALIZER_PASSWORD 不能为空" }
    }
}
