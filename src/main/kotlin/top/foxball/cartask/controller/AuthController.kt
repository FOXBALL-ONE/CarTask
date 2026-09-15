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
class AuthController(
    private val authService: AuthService,
    private val captchaService: CaptchaService,
    private val profileService: ProfileService,
    private val responseBuilder: ResponseBuilder,
    private val workingDepartmentService: WorkingDepartmentService,
    private val smsVerificationService: SmsVerificationService,
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
            /**
             * login：完成身份认证、令牌或验证码处理。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @param command 参与本次处理的输入参数。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
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

    /**
     * 短信验证的生效状态。
     *
     * 临时关闭时前端要跟着跳过验证码步骤（否则界面还在等一条永远收不到的短信），
     * 所以这个状态必须公开可查，且不能缓存。
     */
    @GetMapping("/sms/status")
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

    /** 发送短信验证码：与登录一致，必须同时提交图形验证码，校验通过后一次性作废。 */
    @PostMapping("/sms/send")
    fun sendSms(@RequestBody command: AuthService.SmsSendCommand): ResponseEntity<Response> {
        authService.sendSmsCode(command)
        return responseBuilder.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").data(mapOf("sent" to true)).build()
    }

    @PostMapping("/sms/login")
            /**
             * smsLogin：执行当前模块中的业务操作。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @param command 参与本次处理的输入参数。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
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
            /**
             * resetPassword：更新业务状态或修改相关配置。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @param command 参与本次处理的输入参数。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun resetPassword(@RequestBody command: AuthService.ResetPasswordCommand): ResponseEntity<Response> {
        authService.resetPassword(command)
        return responseBuilder.ok().data(mapOf("reset" to true)).build()
    }

    @GetMapping("/session")
            /**
             * session：执行当前模块中的业务操作。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @param principal 参与本次处理的输入参数。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun session(@AuthenticationPrincipal principal: CurrentUserPrincipal): ResponseEntity<Response> {
        // 会话中的改密标记以 principal 为准（过滤器也按它拦截），头像需回库读取。
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

    /** 当前工作部门与可切换范围；前端顶栏的切换器据此渲染。 */
    @GetMapping("/working-department")
    fun workingDepartment(@AuthenticationPrincipal principal: CurrentUserPrincipal): ResponseEntity<Response> {
        val state = workingDepartmentService.stateOf(principal.userId, principal.role, principal.workingDepartmentId)
        return responseBuilder.ok()
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .data(state.toData())
            .build()
    }

    /**
     * 切换当前工作部门。
     *
     * 只改 Redis 会话，不重新签发 token——工作部门是纯会话态，刻意不进 JWT，
     * 所以切换不必重登，也不会被判成「JWT 与登录状态不匹配」。
     */
    @PutMapping("/working-department")
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
            /**
             * logout：删除、清理或撤销相关数据。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @param principal 参与本次处理的输入参数。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
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
     * 带上头像与改密标记，前端才能在登录成功或刷新页面后立刻渲染头像、并跳转强制改密页；
     * 带上数据范围与工作部门，前端才能渲染顶栏的部门切换器。
     */
    private data class SessionUser(
        @param:JsonProperty("user_id") val userId: Long,
        val username: String,
        val role: String,
        val permissions: List<String>,
        @param:JsonProperty("must_change_password") val mustChangePassword: Boolean,
        val avatar: String?,
        /** ALL / DEPARTMENT / SELF。 */
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

    /**
     * toData：转换、构建或格式化数据。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    private fun WorkingDepartmentState.toData(): WorkingDepartmentData = WorkingDepartmentData(
        currentId,
        currentName,
        scope,
        options.map { DepartmentOptionData(it.id, it.name, it.id == currentId) },
    )

    /** 登录响应与会话查询必须给出完全相同的结构，否则刷新页面后前端状态会漂移。 */
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

    /**
     * toSessionUser：转换、构建或格式化数据。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
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
