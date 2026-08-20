package top.foxball.cartask.logging

import org.slf4j.MDC
import top.foxball.cartask.audit.AuditRequestContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AuditRequestContextTests {
    @Test
    fun `任务上下文写入并在结束后清理`() {
        MDC.put("request_id", "before")
        val result = AuditRequestContext.withRun("task-123") {
            assertEquals("task-123", AuditRequestContext.current()?.requestId)
            assertEquals("task-123", MDC.get("request_id"))
            assertEquals("SYSTEM", MDC.get("actor_type"))
            "done"
        }
        assertEquals("done", result)
        assertNull(AuditRequestContext.current())
        assertEquals("before", MDC.get("request_id"))
        MDC.remove("request_id")
    }

    @Test
    fun `任务上下文异常时也恢复之前的MDC`() {
        MDC.put("actor_type", "USER")
        try {
            kotlin.test.assertFailsWith<IllegalStateException> {
                AuditRequestContext.withRun("task-error") { error("boom") }
            }
            assertEquals("USER", MDC.get("actor_type"))
            assertNull(AuditRequestContext.current())
        } finally {
            MDC.clear()
        }
    }
}
