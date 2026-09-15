package top.foxball.cartask.authentication

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/** 登录失败窗口配置；计数保存在 Redis，避免多实例之间各自放行。 */
@ConfigurationProperties(prefix = "cartask.security.login-rate-limit")
data class LoginRateLimitProperties(
    val enabled: Boolean = true,
    val maxAttempts: Long = 5,
    val window: Duration = Duration.ofMinutes(15),
) {
    /**
     * validate：校验输入、状态或访问条件。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun validate() {
        require(maxAttempts > 0) { "登录限流最大尝试次数必须大于 0" }
        require(window.isPositive) { "登录限流窗口必须大于 0" }
    }
}
