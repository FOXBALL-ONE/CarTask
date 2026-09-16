package top.foxball.cartask.audit

/**
 * AuditRequestContext 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */

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


@Component
/**
 * AuditRequestContextFilter 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
class AuditRequestContextFilter(
    private val operationLogService: OperationLogService,
) : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(AuditRequestContextFilter::class.java)
    
    
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

/**
 * AuditRequestContext 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
object AuditRequestContext {
    private val holder = ThreadLocal<AuditRequestInfo?>()
    
    
    /**
     * current 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun current(): AuditRequestInfo? = holder.get()
    
    
    /**
     * set 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun set(value: AuditRequestInfo) = holder.set(value)
    
    
    /**
     * clear 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun clear() = holder.remove()
    
    
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


