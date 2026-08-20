package top.foxball.cartask.authentication

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/** 登录失败窗口配置；计数保存在 Redis，避免多实例之间各自放行。 */
@ConfigurationProperties(prefix = "shopmall.security.login-rate-limit")
data class LoginRateLimitProperties(
    val enabled: Boolean = true,
    val maxAttempts: Long = 5,
    val window: Duration = Duration.ofMinutes(15),
) {
    fun validate() {
        require(maxAttempts > 0) { "登录限流最大尝试次数必须大于 0" }
        require(window.isPositive) { "登录限流窗口必须大于 0" }
    }
}
