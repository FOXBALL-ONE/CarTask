package top.foxball.cartask.config

import jakarta.annotation.PreDestroy
import org.springframework.stereotype.Component
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 应用重启信号。
 *
 * 系统配置写在 .env 里，而 .env 的值由 DotenvLoader 在启动时灌进 Environment、
 * 再由 @ConfigurationProperties 绑定成 Bean（CorsProperties / SmsProperties / FileProperties
 * 都是构造期绑定的 data class），运行中改了文件不会自动回流到这些 Bean。
 * 因此「保存后重载」这一步只能靠重启整个 Spring 上下文来完成，
 * 与 setup 模块写完 .env 后重启的做法是同一套机制。
 *
 * 这里只负责发信号：由 main() 阻塞等待，收到信号后关闭旧上下文并重新 run。
 * 延迟一小段时间是为了让当前这次 HTTP 响应先写回客户端。
 */
@Component
class AppRestartSignal {
    private val latch = CountDownLatch(1)
    private val restartRequested = AtomicBoolean(false)


    /** request：请求重启。重复调用只有第一次生效。 */
    fun request() {
        if (!restartRequested.compareAndSet(false, true)) return
        Thread {
            runCatching { Thread.sleep(RESTART_DELAY_MILLIS) }
            latch.countDown()
        }.apply {
            isDaemon = true
            name = "app-restart"
        }.start()
    }


    /** await：阻塞到收到重启请求或被关闭为止，返回是否真的需要重启。 */
    fun await(): Boolean {
        latch.await()
        return restartRequested.get()
    }

    @PreDestroy
    /** release：上下文关闭时放行，避免 main() 卡在 await 上。 */
    fun release() {
        latch.countDown()
    }

    private companion object {
        const val RESTART_DELAY_MILLIS = 900L
    }
}
