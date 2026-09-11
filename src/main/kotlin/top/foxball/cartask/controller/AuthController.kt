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
import top.foxball.cartask.shared.Response
import top.foxball.cartask.shared.ResponseBuilder
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
    private val captchaService: CaptchaService,
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
        data class UserData(
            @param:JsonProperty("user_id") val userId: Long,
            val username: kotlin.String,
            val role: kotlin.String,
            val permissions: List<kotlin.String>,
        )
        
        data class Response(
            @param:JsonProperty("access_token") val accessToken: AccessTokenValue,
            @param:JsonProperty("expires_at") val expiresAt: LocalDateTime,
            val user: UserData,
        )
        
        val result = authService.login(command)
        val rs = Response(result.accessToken, result.expiresAt, UserData(result.userId, result.username, result.role, result.permissions.sorted()))
        return responseBuilder.ok()
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${result.accessToken}")
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .header("Pragma", "no-cache")
            .data(rs)
            .build()
    }

    @GetMapping("/session")
    fun session(@AuthenticationPrincipal principal: CurrentUserPrincipal): ResponseEntity<Response> {
        data class Response(
            @param:JsonProperty("user_id") val userId: Long,
            val username: kotlin.String,
            val role: kotlin.String,
            val permissions: List<kotlin.String>,
        )

        val rs = Response(principal.userId, principal.username, principal.role, principal.permissions.sorted())
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
}
