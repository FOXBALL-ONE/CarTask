package top.foxball.cartask.audit

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import top.foxball.cartask.authentication.CurrentUserPrincipal
import top.foxball.cartask.entity.AuditEvent
import top.foxball.cartask.entity.OperationLog
import top.foxball.cartask.logging.OperationLogCommand
import top.foxball.cartask.logging.OperationLogService
import java.time.LocalDateTime
import java.util.*

data class AuditRequestInfo(
    val requestId: String,
    val sourceIp: String?,
    val userAgent: String?,
    val sourceSystem: String = "WEB",
)

/** 为请求生成可回传的关联 ID；任务线程没有 HTTP 上下文时由审计服务使用 SYSTEM 主体。 */
@Component
class AuditRequestContextFilter(
    private val operationLogService: OperationLogService,
) : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(AuditRequestContextFilter::class.java)

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
        val requestedId = request.getHeader("X-Request-Id")?.trim()
        val requestId =
            requestedId?.takeIf { runCatching { UUID.fromString(it) }.isSuccess } ?: UUID.randomUUID().toString()
        val info = AuditRequestInfo(
            requestId = requestId,
            sourceIp = request.remoteAddr?.takeIf(String::isNotBlank),
            userAgent = request.getHeader("User-Agent")?.take(512),
        )
        AuditRequestContext.set(info)
        val previousMdc = MDC.getCopyOfContextMap()
        val startedAt = System.nanoTime()
        var failure: Throwable? = null
        MDC.clear()
        MDC.put("request_id", requestId)
        MDC.put("source_ip", info.sourceIp ?: "")
        MDC.put("source_system", info.sourceSystem)
        MDC.put("actor_type", "ANONYMOUS")
        response.setHeader("X-Request-Id", requestId)
        try {
            filterChain.doFilter(request, response)
        } catch (error: Throwable) {
            failure = error
            throw error
        } finally {
            val durationMs = (System.nanoTime() - startedAt) / 1_000_000
            val principal = SecurityContextHolder.getContext().authentication?.principal as? CurrentUserPrincipal
            val result = when {
                failure != null || response.status >= 500 -> OperationLog.Result.FAILED
                response.status == 401 || response.status == 403 -> OperationLog.Result.DENIED
                else -> OperationLog.Result.SUCCESS
            }
            operationLogService.recordAsync(
                OperationLogCommand(
                    requestId = requestId,
                    actorType = if (principal == null) AuditEvent.ActorType.ANONYMOUS else AuditEvent.ActorType.USER,
                    actorUserId = principal?.userId,
                    actorUsername = principal?.username ?: "anonymous",
                    actorRole = principal?.role,
                    method = request.method,
                    path = request.requestURI,
                    statusCode = response.status,
                    result = result,
                    occurredAt = LocalDateTime.now(),
                    durationMs = durationMs,
                    sourceIp = info.sourceIp,
                    userAgent = info.userAgent,
                    error = failure?.javaClass?.simpleName,
                ),
            )
            MDC.put("duration_ms", durationMs.toString())
            MDC.put("http_method", request.method)
            MDC.put("http_path", request.requestURI)
            MDC.put("http_status", response.status.toString())
            log.info("HTTP 请求完成")
            MDC.clear()
            previousMdc?.let(MDC::setContextMap)
            AuditRequestContext.clear()
        }
    }
}

object AuditRequestContext {
    private val holder = ThreadLocal<AuditRequestInfo?>()

    /**
     * current：查询或读取相关数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun current(): AuditRequestInfo? = holder.get()

    /**
     * set：创建、保存或初始化相关数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param value 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun set(value: AuditRequestInfo) = holder.set(value)

    /**
     * clear：删除、清理或撤销相关数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun clear() = holder.remove()

    /** 在任务或外部回调线程建立可检索的日志关联上下文。调用方必须使用 [withRun]。 */
    fun <T> withRun(requestId: String = UUID.randomUUID().toString(), block: () -> T): T {
        val previous = current()
        val previousMdc = MDC.getCopyOfContextMap()
        set(AuditRequestInfo(requestId = requestId, sourceIp = null, userAgent = null, sourceSystem = "SYSTEM"))
        MDC.clear()
        MDC.put("request_id", requestId)
        MDC.put("source_system", "SYSTEM")
        MDC.put("actor_type", "SYSTEM")
        try {
            return block()
        } finally {
            clear()
            MDC.clear()
            previous?.let(::set)
            previousMdc?.let(MDC::setContextMap)
        }
    }
}
