package top.foxball.cartask.authentication

/**
 * PasswordChangeRequiredFilter：认证子系统中的组件，负责实现相关安全、令牌或访问控制能力。
 *
 * 该文件中的类型和函数用于支撑登录认证流程，并在边界处校验输入与会话状态。
 */

/**
 * PasswordChangeRequiredFilter 认证组件说明。
 *
 * 该文件集中定义认证流程所需的领域类型、服务及基础设施适配逻辑。
 */

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter


@Component
/**
 * PasswordChangeRequiredFilter 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
class PasswordChangeRequiredFilter : OncePerRequestFilter() {
    
    
    /** doFilterInternal：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
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
    
    
    /** isAllowed：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
    private fun isAllowed(request: HttpServletRequest): Boolean {
        if (request.method.equals(HttpMethod.OPTIONS.name(), ignoreCase = true)) {
            return true
        }
        val path = request.requestURI.orEmpty()
        return ALLOWED_PREFIXES.any(path::startsWith)
    }
    
    private companion object {
        
        // 登录前端可能留有一个要求改密的旧 JWT。验证码、登录和短信流程仍是匿名入口，
        // 不能因为该 JWT 被这个过滤器提前拦成 403。
        val ALLOWED_PREFIXES = listOf(
            "/api/profile",
            "/api/auth/logout",
            "/api/auth/session",
            "/api/auth/captcha",
            "/api/auth/login",
            "/api/auth/sms/",
        )
    }
}


