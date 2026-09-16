package top.foxball.setup

/**
 * SetupRestartSignal 配置引导组件说明。
 *
 * 该文件负责系统初始化向导中的配置探测、持久化、校验或 Web 入口逻辑。
 */
/**
 * SetupRestartSignal 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */

import jakarta.annotation.PreDestroy
import org.springframework.stereotype.Component
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean


@Component
/**
 * SetupRestartSignal 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
/**
 * SetupRestartSignal 的职责与行为说明。
 * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
 */
class SetupRestartSignal {
    private val latch = CountDownLatch(1)
    private val restartRequested = AtomicBoolean(false)
    
    
    /**
     * request 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    /**
     * request 的职责与行为说明。
     * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
     */
    fun request() {
        if (!restartRequested.compareAndSet(false, true)) return
        Thread {
            runCatching { Thread.sleep(RESTART_DELAY_MILLIS) }
            latch.countDown()
        }.apply {
            isDaemon = true
            name = "setup-restart"
        }.start()
    }
    
    
    /**
     * await 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    /**
     * await 的职责与行为说明。
     * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
     */
    fun await(): Boolean {
        latch.await()
        return restartRequested.get()
    }
    
    @PreDestroy
            
            
            /**
             * release 函数：执行与该组件职责相关的业务操作。
             * 参数和返回值遵循调用方与领域服务之间的约定。
             */
            /**
             * release 的职责与行为说明。
             * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
             */
    fun release() {
        latch.countDown()
    }
    
    private companion object {
        const val RESTART_DELAY_MILLIS = 900L
    }
}





