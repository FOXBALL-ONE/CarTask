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
            /**
             * operationLogExecutor：执行当前模块中的业务操作。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @param properties 参与本次处理的输入参数。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun operationLogExecutor(properties: LoggingProperties): ThreadPoolTaskExecutor = ThreadPoolTaskExecutor().apply {
        corePoolSize = 2
        maxPoolSize = 8
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
}
