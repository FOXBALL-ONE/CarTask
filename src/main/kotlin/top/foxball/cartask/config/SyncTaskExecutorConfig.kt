package top.foxball.cartask.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

@Configuration
@EnableScheduling
class SyncTaskExecutorConfig {
    @Bean("syncTaskScheduler")
    fun syncTaskScheduler(): ThreadPoolTaskScheduler = ThreadPoolTaskScheduler().apply {
        poolSize = 4
        setThreadNamePrefix("sync-task-scheduler-")
        setAwaitTerminationSeconds(60)
        setWaitForTasksToCompleteOnShutdown(true)
        initialize()
    }
}
