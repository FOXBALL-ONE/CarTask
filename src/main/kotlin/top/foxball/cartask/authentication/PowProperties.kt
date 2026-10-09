package top.foxball.cartask.authentication

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "cartask.security.pow")
data class PowProperties(
    var enabled: Boolean = true,
    var replaceCaptcha: Boolean = true,
    var combineCaptcha: Boolean = false,
    var fallbackToCaptcha: Boolean = true,
) {
    fun validate() {
        require(!(replaceCaptcha && combineCaptcha)) { "POW 替换图形验证码与双重验证不能同时开启" }
    }

    fun requiresPow(): Boolean = enabled

    fun requiresCaptcha(): Boolean = !enabled || !replaceCaptcha || combineCaptcha

    fun allowsCaptchaFallback(): Boolean = enabled && replaceCaptcha && !combineCaptcha && fallbackToCaptcha
}
