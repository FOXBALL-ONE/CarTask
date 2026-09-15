package top.foxball.cartask.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * 生成备份期间挡下其他请求，返回 503 并给出重试建议。
 *
 * 放在过滤器链最前面（早于安全链）：此时系统正在静默，连鉴权都不必跑，也就不会往审计表里写一堆
 * 因为备份而被拒的「授权拒绝」噪声。
 *
 * 放行清单只有两项：
 *  - 备份接口自身（路径前缀见 [BACKUP_PATH_PREFIX]），否则发起备份的请求会被自己挡掉；
 *  - 健康检查（[ACTUATOR_PATH_PREFIX]），要一直可答，不然监控会把「正在备份」报成「服务挂了」。
 *
 * 重复发起的备份请求虽然放行，但会撞上服务里的导出锁拿到 409，不会被排成第二次导出。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
class MaintenanceFilter(
    private val maintenanceGate: MaintenanceGate,
) : OncePerRequestFilter() {
    /**
     * shouldNotFilter：校验输入、状态或访问条件。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param request 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val uri = request.requestURI
        return uri.startsWith(BACKUP_PATH_PREFIX) || uri.startsWith(ACTUATOR_PATH_PREFIX)
    }

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
