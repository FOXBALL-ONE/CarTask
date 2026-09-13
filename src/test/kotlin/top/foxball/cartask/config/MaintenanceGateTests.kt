package top.foxball.cartask.config

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * 静默闸门是「备份期间不响应其他请求和同步任务」的唯一实现，判断错一次就是数据被污染或系统假死，
 * 所以把互斥、可重入和恢复这三条分别锁住。
 */
class MaintenanceGateTests {
    private val gate = MaintenanceGate()

    private fun withBackupRunning(block: () -> Unit) {
        val holding = CountDownLatch(1)
        val release = CountDownLatch(1)
        val holder = thread {
            gate.runExclusive {
                holding.countDown()
                release.await()
            }
        }
        assertTrue(holding.await(5, TimeUnit.SECONDS), "没能进入备份状态")
        try {
            block()
        } finally {
            release.countDown()
            holder.join(5_000)
        }
    }

    @Test
    fun `备份生成期间普通操作一律被拒`() {
        assertTrue(gate.enterNormalOperation())
        gate.leaveNormalOperation()

        withBackupRunning {
            assertFalse(gate.enterNormalOperation(), "备份期间不该再放行任何请求或同步任务")
        }
    }

    @Test
    fun `备份结束后立刻恢复正常`() {
        withBackupRunning {
            assertFalse(gate.enterNormalOperation())
        }

        assertTrue(gate.enterNormalOperation(), "备份结束后必须立刻恢复，闸门不能被一次执行长期占着")
        gate.leaveNormalOperation()
    }

    @Test
    fun `同一线程重复进出不会把读锁多还一次`() {
        assertTrue(gate.enterNormalOperation())
        // 手工触发的同步跑在请求线程里，那里过滤器已经拿过一次读锁，这次是重入。
        assertTrue(gate.enterNormalOperation())
        gate.leaveNormalOperation()
        gate.leaveNormalOperation()

        // 多还一次会抛 IllegalMonitorStateException，少还一次会让备份永远等不到写锁；两者都在这里暴露。
        assertTrue(gate.runExclusive { true })
    }

    @Test
    fun `备份会等在途的读操作结束再开始`() {
        assertTrue(gate.enterNormalOperation())
        val acquired = CountDownLatch(1)
        val backup = thread {
            gate.runExclusive { acquired.countDown() }
        }

        // 读锁还在手上时，备份必须进不去——否则它会在一个正在写入的过程中开始导出。
        assertFalse(acquired.await(300, TimeUnit.MILLISECONDS), "读操作未结束就开始了备份")

        gate.leaveNormalOperation()
        assertTrue(acquired.await(5, TimeUnit.SECONDS), "读操作结束后备份应当接着开始")
        backup.join(5_000)
    }
}
