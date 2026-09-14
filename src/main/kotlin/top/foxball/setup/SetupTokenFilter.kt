package top.foxball.setup

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * 引导接口的配置口令校验。
 *
 * 放在过滤器链最前面（早于安全链）：口令不对的请求连 Spring Security 都不必走，也就不会在日志里
 * 留下一堆和引导无关的告警。
 *
 * 只有两个例外：
 *  - `/api/setup/status`：前端在跳转到引导页之前必须先问「要不要引导」，此时还没有口令可用；
 *  - `OPTIONS` 预检：浏览器不会在预检请求上带自定义头，拦下来会让向导在跨域下直接不可用。
 *
 * 状态码用 403 而不是 401：没有口令不等于「登录态失效」，前端那条「401 就清 token 跳登录页」的
 * 通用逻辑不该被引导接口触发——引导阶段根本没有 token 可清。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class SetupTokenFilter(
    private val token: SetupToken,
) : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = path(request)
        if (!path.startsWith(PATH_PREFIX)) return true
        if (request.method == HttpMethod.OPTIONS.name()) return true
        return path == STATUS_PATH
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        if (!token.matches(request.getHeader(HEADER))) {
            response.status = HttpServletResponse.SC_FORBIDDEN
            response.contentType = "application/json;charset=UTF-8"
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store")
            response.writer.write(
                """{"status":403,"success":false,"message":"$MISSING_TOKEN_MESSAGE"}""",
            )
            return
        }
        filterChain.doFilter(request, response)
    }

    /** 去掉 context path，避免部署在子路径下时前缀判断失配。 */
    private fun path(request: HttpServletRequest): String =
        request.requestURI.removePrefix(request.contextPath)

    companion object {
        /** 引导页在首次提交时把口令放进这个头；接口全部要求它，除了免检的 status。 */
        const val HEADER = "X-Setup-Token"

        const val PATH_PREFIX = "/api/setup/"
        const val STATUS_PATH = "/api/setup/status"

        private const val MISSING_TOKEN_MESSAGE =
            "配置口令不正确。请在服务启动日志的「配置引导模式」横幅中查看本次口令。"
    }
}
