package top.foxball.cartask.controller

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import top.foxball.cartask.authentication.*
import top.foxball.cartask.scope.WorkingDepartmentService
import top.foxball.cartask.scope.WorkingDepartmentState
import top.foxball.cartask.service.ProfileService
import top.foxball.cartask.shared.Response
import top.foxball.cartask.shared.ResponseBuilder
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/auth")
/** class AuthController：Web 控制器，负责接收 HTTP 请求、调用领域服务并构造统一响应。 */
class AuthController(
    private val authService: AuthService,
    private val captchaService: CaptchaService,
    private val profileService: ProfileService,
    private val responseBuilder: ResponseBuilder,
    private val workingDepartmentService: WorkingDepartmentService,
    private val smsVerificationService: SmsVerificationService,
) {
    
    @GetMapping("/captcha")
            /** captcha：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun captcha(): ResponseEntity<Response> {
        val captcha = captchaService.generate()
        return responseBuilder.ok()
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .data(captcha)
            .build()
    }
    
    @PostMapping("/login")
            
            
            /** login：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
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
    
    
    @GetMapping("/sms/status")
            /** smsStatus：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun smsStatus(): ResponseEntity<Response> {
        data class Response(
            @param:JsonProperty("verification_enabled") val verificationEnabled: Boolean,
        )
        
        val rs = Response(!smsVerificationService.verificationSkipped)
        return responseBuilder.ok()
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .data(rs)
            .build()
    }
    
    
    @PostMapping("/sms/send")
            /** sendSms：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun sendSms(@RequestBody command: AuthService.SmsSendCommand): ResponseEntity<Response> {
        authService.sendSmsCode(command)
        return responseBuilder.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").data(mapOf("sent" to true)).build()
    }
    
    @PostMapping("/sms/login")
            
            
            /** smsLogin：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
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
            
            
            /** resetPassword：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun resetPassword(@RequestBody command: AuthService.ResetPasswordCommand): ResponseEntity<Response> {
        authService.resetPassword(command)
        return responseBuilder.ok().data(mapOf("reset" to true)).build()
    }
    
    @GetMapping("/session")
            
            
            /** session：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun session(@AuthenticationPrincipal principal: CurrentUserPrincipal): ResponseEntity<Response> {
        val rs = sessionUserOf(
            principal.userId,
            principal.username,
            principal.role,
            principal.permissions.sorted(),
            principal.mustChangePassword,
            profileService.avatarOf(principal.userId),
            principal.workingDepartmentId,
        )
        return responseBuilder.ok()
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .data(rs)
            .build()
    }
    
    
    @GetMapping("/working-department")
            /** workingDepartment：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun workingDepartment(@AuthenticationPrincipal principal: CurrentUserPrincipal): ResponseEntity<Response> {
        val state = workingDepartmentService.stateOf(principal.userId, principal.role, principal.workingDepartmentId)
        return responseBuilder.ok()
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .data(state.toData())
            .build()
    }
    
    
    @PutMapping("/working-department")
            /** switchWorkingDepartment：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun switchWorkingDepartment(
        @AuthenticationPrincipal principal: CurrentUserPrincipal,
        @RequestParam(name = "department_id", required = false) departmentId: Long?,
    ): ResponseEntity<Response> {
        val state = workingDepartmentService.switchTo(principal, departmentId)
        return responseBuilder.ok()
            .message("工作部门已切换")
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .data(state.toData())
            .build()
    }
    
    @PostMapping("/logout")
            
            
            /** logout：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun logout(@AuthenticationPrincipal principal: CurrentUserPrincipal): ResponseEntity<Response> {
        data class Response(@param:JsonProperty("logged_out") val loggedOut: Boolean)
        
        authService.logout(principal.tokenId)
        val rs = Response(true)
        return responseBuilder.ok()
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .data(rs)
            .build()
    }
    
    
    private data class SessionUser(
        @param:JsonProperty("user_id") val userId: Long,
        val username: String,
        val role: String,
        val permissions: List<String>,
        @param:JsonProperty("must_change_password") val mustChangePassword: Boolean,
        val avatar: String?,
        
        val scope: String,
        @param:JsonProperty("working_department_id") val workingDepartmentId: Long?,
        @param:JsonProperty("working_department_name") val workingDepartmentName: String?,
        @param:JsonProperty("working_department_options") val workingDepartmentOptions: List<DepartmentOptionData>,
    )
    
    private data class DepartmentOptionData(
        val id: Long,
        val name: String,
        val selected: Boolean,
    )
    
    private data class WorkingDepartmentData(
        @param:JsonProperty("current_id") val currentId: Long?,
        @param:JsonProperty("current_name") val currentName: String?,
        val scope: String,
        val options: List<DepartmentOptionData>,
    )
    
    
    private fun WorkingDepartmentState.toData(): WorkingDepartmentData = WorkingDepartmentData(
        currentId,
        currentName,
        scope,
        options.map { DepartmentOptionData(it.id, it.name, it.id == currentId) },
    )
    
    
    private fun sessionUserOf(
        userId: Long,
        username: String,
        role: String,
        permissions: List<String>,
        mustChangePassword: Boolean,
        avatar: String?,
        workingDepartmentId: Long?,
    ): SessionUser {
        val state = workingDepartmentService.stateOf(userId, role, workingDepartmentId)
        return SessionUser(
            userId,
            username,
            role,
            permissions,
            mustChangePassword,
            avatar,
            state.scope,
            state.currentId,
            state.currentName,
            state.options.map { DepartmentOptionData(it.id, it.name, it.id == state.currentId) },
        )
    }
    
    
    private fun AuthService.LoginData.toSessionUser(): SessionUser = sessionUserOf(
        userId,
        username,
        role,
        permissions.sorted(),
        mustChangePassword,
        avatar,
        workingDepartmentId,
    )
}
