package top.foxball.cartask.audit

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

data class AuditRequestInfo(
    val requestId: String,
    val sourceIp: String?,
    val userAgent: String?,
    val sourceSystem: String = "WEB",
)

/** 为请求生成可回传的关联 ID；任务线程没有 HTTP 上下文时由审计服务使用 SYSTEM 主体。 */
@Component
class AuditRequestContextFilter : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(AuditRequestContextFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val requestedId = request.getHeader("X-Request-Id")?.trim()
        val requestId = requestedId?.takeIf { runCatching { UUID.fromString(it) }.isSuccess } ?: UUID.randomUUID().toString()
        val info = AuditRequestInfo(
            requestId = requestId,
            sourceIp = request.remoteAddr?.takeIf(String::isNotBlank),
            userAgent = request.getHeader("User-Agent")?.take(512),
        )
        AuditRequestContext.set(info)
        val previousMdc = MDC.getCopyOfContextMap()
        val startedAt = System.nanoTime()
        MDC.clear()
        MDC.put("request_id", requestId)
        MDC.put("source_ip", info.sourceIp ?: "")
        MDC.put("source_system", info.sourceSystem)
        MDC.put("actor_type", "ANONYMOUS")
        response.setHeader("X-Request-Id", requestId)
        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.put("duration_ms", ((System.nanoTime() - startedAt) / 1_000_000).toString())
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

    fun current(): AuditRequestInfo? = holder.get()
    fun set(value: AuditRequestInfo) = holder.set(value)
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
