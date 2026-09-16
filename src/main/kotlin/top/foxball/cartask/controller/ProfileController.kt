package top.foxball.cartask.controller

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import top.foxball.cartask.authentication.CurrentUserPrincipal
import top.foxball.cartask.service.ProfileService
import top.foxball.cartask.shared.Response
import top.foxball.cartask.shared.ResponseBuilder

@RestController
@RequestMapping("/api/profile")


/** class ProfileController：Web 控制器，负责接收 HTTP 请求、调用领域服务并构造统一响应。 */
class ProfileController(
    private val profileService: ProfileService,
    private val responseBuilder: ResponseBuilder,
) {
    
    @GetMapping
            /** get：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun get(@AuthenticationPrincipal principal: CurrentUserPrincipal): ResponseEntity<Response> {
        val rs = profileService.get(principal.userId)
        return responseBuilder.ok()
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .data(rs)
            .build()
    }
    
    
    @PutMapping
            /** update：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun update(
        @AuthenticationPrincipal principal: CurrentUserPrincipal,
        @RequestBody command: ProfileService.UpdateCommand,
    ): ResponseEntity<Response> {
        val rs = profileService.update(principal.userId, command)
        return responseBuilder.ok()
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .data(rs)
            .build()
    }
    
    
    @PutMapping("/phone")
            /** changePhone：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun changePhone(
        @AuthenticationPrincipal principal: CurrentUserPrincipal,
        @RequestBody command: ProfileService.ChangePhoneCommand,
    ): ResponseEntity<Response> {
        val rs = profileService.changePhone(principal.userId, command)
        return responseBuilder.ok()
            .message("手机号已更新")
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .data(rs)
            .build()
    }
    
    
    @PutMapping("/password")
            /** changePassword：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun changePassword(
        @AuthenticationPrincipal principal: CurrentUserPrincipal,
        @RequestBody command: ProfileService.ChangePasswordCommand,
    ): ResponseEntity<Response> {
        data class Response(@param:JsonProperty("password_changed") val passwordChanged: Boolean)
        
        profileService.changePassword(principal.userId, command)
        return responseBuilder.ok()
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .data(Response(true))
            .build()
    }
    
    
    @PutMapping("/avatar")
            /** updateAvatar：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun updateAvatar(
        @AuthenticationPrincipal principal: CurrentUserPrincipal,
        @RequestBody command: ProfileService.AvatarCommand,
    ): ResponseEntity<Response> {
        val rs = profileService.updateAvatar(principal.userId, command)
        return responseBuilder.ok()
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .data(rs)
            .build()
    }
}
