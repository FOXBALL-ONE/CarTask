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
    /**
     * validate：校验输入、状态或访问条件。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun validate() {
        require(username.isNotBlank()) { "ADMIN_INITIALIZER_USERNAME 不能为空" }
        require(password.isNotBlank()) { "ADMIN_INITIALIZER_PASSWORD 不能为空" }
    }
}
