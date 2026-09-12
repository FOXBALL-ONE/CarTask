package top.foxball.cartask.controller

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import top.foxball.cartask.authentication.AccessTokenValue
import top.foxball.cartask.authentication.AuthService
import top.foxball.cartask.authentication.CaptchaService
import top.foxball.cartask.authentication.CurrentUserPrincipal
import top.foxball.cartask.service.ProfileService
import top.foxball.cartask.shared.Response
import top.foxball.cartask.shared.ResponseBuilder
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
    private val captchaService: CaptchaService,
    private val profileService: ProfileService,
    private val responseBuilder: ResponseBuilder,
) {
    /** 登录图形验证码：无需认证，返回一次性 token 与 base64 SVG 图片。 */
    @GetMapping("/captcha")
    fun captcha(): ResponseEntity<Response> {
        val captcha = captchaService.generate()
        return responseBuilder.ok()
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .data(captcha)
            .build()
    }

    @PostMapping("/login")
    fun login(@RequestBody command: AuthService.LoginCommand): ResponseEntity<Response> {
        data class Response(
            @param:JsonProperty("access_token") val accessToken: AccessTokenValue,
            @param:JsonProperty("expires_at") val expiresAt: LocalDateTime,
            val user: SessionUser,
        )

        val result = authService.login(command)
        val rs = Response(result.accessToken, result.expiresAt, result.toSessionUser())
        return responseBuilder.ok()
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${result.accessToken}")
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .header("Pragma", "no-cache")
            .data(rs)
            .build()
    }

    /** 发送短信验证码：与登录一致，必须同时提交图形验证码，校验通过后一次性作废。 */
    @PostMapping("/sms/send")
    fun sendSms(@RequestBody command: AuthService.SmsSendCommand): ResponseEntity<Response> {
        authService.sendSmsCode(command)
        return responseBuilder.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").data(mapOf("sent" to true)).build()
    }

    @PostMapping("/sms/login")
    fun smsLogin(@RequestBody command: AuthService.SmsLoginCommand): ResponseEntity<Response> {
        val result = authService.loginBySms(command)
        data class LoginResponse(
            @param:JsonProperty("access_token") val accessToken: AccessTokenValue,
            @param:JsonProperty("expires_at") val expiresAt: LocalDateTime,
            val user: SessionUser,
        )
        val rs = LoginResponse(result.accessToken, result.expiresAt, result.toSessionUser())
        return responseBuilder.ok()
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${result.accessToken}")
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .header("Pragma", "no-cache")
            .data(rs)
            .build()
    }

    @PostMapping("/sms/reset-password")
    fun resetPassword(@RequestBody command: AuthService.ResetPasswordCommand): ResponseEntity<Response> {
        authService.resetPassword(command)
        return responseBuilder.ok().data(mapOf("reset" to true)).build()
    }

    @GetMapping("/session")
    fun session(@AuthenticationPrincipal principal: CurrentUserPrincipal): ResponseEntity<Response> {
        // 会话中的改密标记以 principal 为准（过滤器也按它拦截），头像需回库读取。
        val rs = SessionUser(
            principal.userId,
            principal.username,
            principal.role,
            principal.permissions.sorted(),
            principal.mustChangePassword,
            profileService.avatarOf(principal.userId),
        )
        return responseBuilder.ok()
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .data(rs)
            .build()
    }
    
    @PostMapping("/logout")
    fun logout(@AuthenticationPrincipal principal: CurrentUserPrincipal): ResponseEntity<Response> {
        data class Response(@param:JsonProperty("logged_out") val loggedOut: Boolean)
        
        authService.logout(principal.tokenId)
        val rs = Response(true)
        return responseBuilder.ok()
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .data(rs)
            .build()
    }

    /**
     * 登录与登录态查询共用的用户摘要。
     * 带上头像与改密标记，前端才能在登录成功或刷新页面后立刻渲染头像、并跳转强制改密页。
     */
    private data class SessionUser(
        @param:JsonProperty("user_id") val userId: Long,
        val username: kotlin.String,
        val role: kotlin.String,
        val permissions: List<kotlin.String>,
        @param:JsonProperty("must_change_password") val mustChangePassword: Boolean,
        val avatar: kotlin.String?,
    )

    private fun AuthService.LoginData.toSessionUser(): SessionUser = SessionUser(
        userId,
        username,
        role,
        permissions.sorted(),
        mustChangePassword,
        avatar,
    )
}
