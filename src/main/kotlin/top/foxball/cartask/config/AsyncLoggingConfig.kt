package top.foxball.cartask.config

import org.slf4j.MDC
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import top.foxball.cartask.audit.AuditRequestContext
import top.foxball.cartask.logging.LoggingProperties
import java.util.concurrent.ThreadPoolExecutor

@Configuration
@EnableAsync
class AsyncLoggingConfig {
    @Bean("operationLogExecutor")
    fun operationLogExecutor(properties: LoggingProperties): ThreadPoolTaskExecutor = ThreadPoolTaskExecutor().apply {
        corePoolSize = 2
        maxPoolSize = 8
        queueCapacity = properties.queueSize.coerceAtLeast(128)
        setKeepAliveSeconds(60)
        setThreadNamePrefix("operation-log-")
        setTaskDecorator { delegate ->
            val securityContext: SecurityContext = SecurityContextHolder.getContext()
            val requestContext = AuditRequestContext.current()
            val mdcContext = MDC.getCopyOfContextMap()
            Runnable {
                val previousSecurityContext = SecurityContextHolder.getContext()
                val previousRequestContext = AuditRequestContext.current()
                val previousMdcContext = MDC.getCopyOfContextMap()
                SecurityContextHolder.setContext(securityContext)
                if (requestContext == null) AuditRequestContext.clear() else AuditRequestContext.set(requestContext)
                if (mdcContext == null) MDC.clear() else MDC.setContextMap(mdcContext)
                try {
                    delegate.run()
                } finally {
                    SecurityContextHolder.setContext(previousSecurityContext)
                    if (previousRequestContext == null) AuditRequestContext.clear() else AuditRequestContext.set(previousRequestContext)
                    MDC.clear()
                    previousMdcContext?.let(MDC::setContextMap)
                }
            }
        }
        setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())
        initialize()
    }
}
