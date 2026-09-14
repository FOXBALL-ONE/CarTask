package top.foxball.cartask.controller

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import top.foxball.cartask.authentication.CurrentUserPrincipal
import top.foxball.cartask.service.ProfileService
import top.foxball.cartask.shared.Response
import top.foxball.cartask.shared.ResponseBuilder

@RestController
@RequestMapping("/api/profile")
/**
 * 个人中心接口。操作对象固定为登录态自身，不接受路径或请求体指定用户，
 * 因此无需额外权限校验——任何已登录用户都只能管理自己的资料与密码。
 */
class ProfileController(
    private val profileService: ProfileService,
    private val responseBuilder: ResponseBuilder,
) {
    /** 查询当前登录用户的个人资料。 */
    @GetMapping
    fun get(@AuthenticationPrincipal principal: CurrentUserPrincipal): ResponseEntity<Response> {
        val rs = profileService.get(principal.userId)
        return responseBuilder.ok()
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .data(rs)
            .build()
    }

    /** 修改当前登录用户的昵称、邮箱与性别。手机号是登录凭据，必须走 [changePhone]。 */
    @PutMapping
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

    /**
     * 换绑当前登录用户的手机号。
     *
     * 验证码由 `POST /api/auth/sms/send`（purpose=CHANGE_PHONE）发到新号码上，本接口只负责校验；
     * 管理员改他人手机号不走这里，也不需要短信校验。
     */
    @PutMapping("/phone")
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

    /** 修改密码；成功后服务端会撤销该用户全部历史会话，需要重新登录。 */
    @PutMapping("/password")
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

    /** 更新头像；请求体中的 data URL 为空字符串时清除头像。 */
    @PutMapping("/avatar")
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
