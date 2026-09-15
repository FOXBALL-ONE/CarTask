package top.foxball.cartask.authentication

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * 初始密码未修改前，除改密必需的接口外一律拒绝。
 *
 * 判定依据是 [JwtAuthenticationFilter] 已经从 Redis 会话中带出的标记，本过滤器不再查库，
 * 因此「首次必须改密」是服务端强制的，绕过前端直接调用接口同样会被拦截。
 */
@Component
class PasswordChangeRequiredFilter : OncePerRequestFilter() {

    /**
     * doFilterInternal：处理请求、事件或异常流程。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param request 参与本次处理的输入参数。
     * @param response 参与本次处理的输入参数。
     * @param filterChain 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val principal = SecurityContextHolder.getContext().authentication?.principal as? CurrentUserPrincipal
        if (principal == null || !principal.mustChangePassword || isAllowed(request)) {
            filterChain.doFilter(request, response)
            return
        }
        response.status = HttpServletResponse.SC_FORBIDDEN
        response.contentType = "application/json;charset=UTF-8"
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store")
        response.writer.write(
            """{"status":403,"success":false,"message":"首次登录必须先修改密码","data":{"password_change_required":true}}""",
        )
    }

    /**
     * isAllowed：校验输入、状态或访问条件。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param request 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    private fun isAllowed(request: HttpServletRequest): Boolean {
        if (request.method.equals(HttpMethod.OPTIONS.name(), ignoreCase = true)) {
            return true
        }
        val path = request.requestURI.orEmpty()
        return ALLOWED_PREFIXES.any(path::startsWith)
    }

    private companion object {
        /** 改密前后仍需可用的接口：个人资料读写（含改密、设置头像）、登出与会话续期。 */
        val ALLOWED_PREFIXES = listOf("/api/profile", "/api/auth/logout", "/api/auth/session")
    }
}
