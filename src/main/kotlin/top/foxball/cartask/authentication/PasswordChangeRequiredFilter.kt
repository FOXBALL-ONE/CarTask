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
