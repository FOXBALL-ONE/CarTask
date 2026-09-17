package top.foxball.cartask.config

/**
 * AsyncLoggingConfig 组件。
 *
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */

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
/**
 * AsyncLoggingConfig 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
class AsyncLoggingConfig {
    @Bean("operationLogExecutor")


            /**
             * operationLogExecutor 函数：执行与该组件职责相关的业务操作。
             * 参数和返回值遵循调用方与领域服务之间的约定。
             */
    fun operationLogExecutor(properties: LoggingProperties): ThreadPoolTaskExecutor = ThreadPoolTaskExecutor().apply {
        corePoolSize = 4
        maxPoolSize = 16
        queueCapacity = properties.queueSize.coerceAtLeast(128)
        keepAliveSeconds = 60
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
                    if (previousRequestContext == null) AuditRequestContext.clear() else AuditRequestContext.set(
                        previousRequestContext
                    )
                    MDC.clear()
                    previousMdcContext?.let(MDC::setContextMap)
                }
            }
        }
        setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())
        initialize()
    }

    @Bean("syncTaskExecutor")
    fun syncTaskExecutor(): ThreadPoolTaskExecutor = ThreadPoolTaskExecutor().apply {
        corePoolSize = 3
        maxPoolSize = 8
        queueCapacity = 256
        keepAliveSeconds = 60
        setThreadNamePrefix("sync-task-")
        setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())
        initialize()
    }
}


