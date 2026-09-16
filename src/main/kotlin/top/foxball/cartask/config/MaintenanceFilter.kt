package top.foxball.cartask.config

/**
 * MaintenanceFilter 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter


@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
/**
 * MaintenanceFilter 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
class MaintenanceFilter(
    private val maintenanceGate: MaintenanceGate,
) : OncePerRequestFilter() {
    
    
    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val uri = request.requestURI
        return uri.startsWith(BACKUP_PATH_PREFIX) || uri.startsWith(ACTUATOR_PATH_PREFIX)
    }
    
    
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        if (!maintenanceGate.enterNormalOperation()) {
            response.status = HttpServletResponse.SC_SERVICE_UNAVAILABLE
            response.contentType = "application/json;charset=UTF-8"
            response.setHeader(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store")
            response.writer.write(
                """{"status":503,"success":false,"message":"$BUSY_MESSAGE"}""",
            )
            return
        }
        try {
            filterChain.doFilter(request, response)
        } finally {
            maintenanceGate.leaveNormalOperation()
        }
    }
    
    private companion object {
        const val BACKUP_PATH_PREFIX = "/api/backup/"
        const val ACTUATOR_PATH_PREFIX = "/actuator/"
        const val RETRY_AFTER_SECONDS = "30"
        const val BUSY_MESSAGE = "系统正在生成数据备份，期间暂停响应其他操作，请稍后重试"
    }
}


