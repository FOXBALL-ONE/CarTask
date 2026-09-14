package top.foxball.setup

import jakarta.annotation.PreDestroy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import org.springframework.stereotype.Component

/**
 * 配置完成后的「重启」信号：让 `main` 关掉配置模式上下文，按新的 `.env` 再启动一次。
 *
 * 不做进程退出再拉起，是因为引导页刚提交完就断连，实施人员看到的只有「连接被拒绝」，
 * 分不清是重启中还是启动失败。同一个 JVM 里换上下文，端口只断开一瞬，页面轮询就能穿过这段空窗。
 *
 * 延迟 [RESTART_DELAY_MILLIS] 再触发：当前这次请求的响应还没写完就关上下文，浏览器拿到的是网络错误
 * 而不是「配置已保存」。延迟由独立线程承担，请求线程照常返回。
 */
@Component
class SetupRestartSignal {
    private val latch = CountDownLatch(1)
    private val restartRequested = AtomicBoolean(false)

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
     * 阻塞至可以继续为止。
     *
     * 返回 true 表示收到重启请求；返回 false 表示上下文已被别的路径关闭（例如 Ctrl+C 触发的
     * 关闭钩子），此时 `main` 应当直接退出。两种情况都要 [CountDownLatch.countDown]，
     * 否则关闭钩子里等着的那个线程永远醒不过来，表现为 Ctrl+C 之后进程不退出。
     */
    fun await(): Boolean {
        latch.await()
        return restartRequested.get()
    }

    @PreDestroy
    fun release() {
        latch.countDown()
    }

    private companion object {
        const val RESTART_DELAY_MILLIS = 900L
    }
}
