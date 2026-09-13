package top.foxball.cartask.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class MaintenanceFilterTests {
    private val gate = MaintenanceGate()
    private val filter = MaintenanceFilter(gate)

    private fun request(uri: String): MockHttpServletRequest =
        MockHttpServletRequest("GET", uri).apply { requestURI = uri }

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
    fun `备份期间普通请求返回 503 且不进入后续链路`() {
        withBackupRunning {
            val response = MockHttpServletResponse()
            val chain = MockFilterChain()

            filter.doFilter(request("/api/users"), response, chain)

            assertEquals(503, response.status)
            assertTrue(response.contentAsString.contains("正在生成数据备份"), response.contentAsString)
            assertEquals("30", response.getHeader("Retry-After"))
            assertNull(chain.request, "被挡下的请求不该继续往下走")
        }
    }

    @Test
    fun `备份期间放行备份接口与健康检查`() {
        withBackupRunning {
            listOf("/api/backup/export", "/api/backup/summary", "/actuator/health").forEach { uri ->
                val response = MockHttpServletResponse()
                val chain = MockFilterChain()

                filter.doFilter(request(uri), response, chain)

                assertEquals(200, response.status, "$uri 应当被放行，否则发起的备份会把自己挡掉")
                assertTrue(chain.request != null, "$uri 应当继续走后续链路")
            }
        }
    }

    @Test
    fun `普通请求结束后释放读锁，备份随后可以开始`() {
        val response = MockHttpServletResponse()
        filter.doFilter(request("/api/users"), response, MockFilterChain())
        assertEquals(200, response.status)

        // 请求处理期间拿到的是读锁，如果 finally 里没还，备份将永远等不到写锁。
        val acquired = CountDownLatch(1)
        val backup = thread { gate.runExclusive { acquired.countDown() } }
        assertTrue(acquired.await(5, TimeUnit.SECONDS), "请求结束后读锁没有被释放")
        backup.join(5_000)
    }

    @Test
    fun `放行的备份接口同样会释放读锁`() {
        val response = MockHttpServletResponse()
        filter.doFilter(request("/api/backup/summary"), response, MockFilterChain())

        val acquired = CountDownLatch(1)
        val backup = thread { gate.runExclusive { acquired.countDown() } }
        assertTrue(acquired.await(5, TimeUnit.SECONDS), "备份接口既已放行就不该再持锁")
        backup.join(5_000)
    }
}
