package top.foxball.setup

/**
 * SetupTokenFilter 配置引导组件说明。
 *
 * 该文件负责系统初始化向导中的配置探测、持久化、校验或 Web 入口逻辑。
 */
/**
 * SetupTokenFilter 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter


@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
/**
 * SetupTokenFilter 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
/**
 * SetupTokenFilter 的职责与行为说明。
 * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
 */
class SetupTokenFilter(
    private val token: SetupToken,
) : OncePerRequestFilter() {
    
    
    /**
     * shouldNotFilter 的职责与行为说明。
     * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
     */
    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = path(request)
        if (!path.startsWith(PATH_PREFIX)) return true
        if (request.method == HttpMethod.OPTIONS.name()) return true
        return path == STATUS_PATH
    }
    
    
    /**
     * doFilterInternal 的职责与行为说明。
     * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
     */
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
    
    
    /**
     * path 的职责与行为说明。
     * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
     */
    private fun path(request: HttpServletRequest): String =
        request.requestURI.removePrefix(request.contextPath)
    
    companion object {
        
        const val HEADER = "X-Setup-Token"
        
        const val PATH_PREFIX = "/api/setup/"
        const val STATUS_PATH = "/api/setup/status"
        
        private const val MISSING_TOKEN_MESSAGE =
            "配置口令不正确。请在服务启动日志的「配置引导模式」横幅中查看本次口令。"
    }
}





