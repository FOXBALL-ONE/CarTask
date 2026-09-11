package top.foxball.cartask.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.LoggingEvent
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.slf4j.MarkerFactory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JsonLogLayoutTests {
    @Test
    fun `日志输出为单行并将耗时输出为数值`() {
        MDC.put("request_id", "request-1")
        MDC.put("duration_ms", "42")
        try {
            val logger = LoggerFactory.getLogger("logging-test") as Logger
            val event = LoggingEvent().apply {
                this.loggerName = logger.name
                this.level = Level.INFO
                this.message = "hello\nworld"
                this.argumentArray = emptyArray()
                this.mdcPropertyMap = MDC.getCopyOfContextMap()
                this.timeStamp = 0L
            }
            val layout = JsonLogLayout().apply { start() }
            val output = layout.doLayout(event)
            assertTrue(output.endsWith("\n"))
            assertFalse(output.dropLast(1).contains('\n'))
            assertTrue("\"duration_ms\":42" in output)
            layout.stop()
        } finally {
            MDC.clear()
        }
    }

    @Test
    fun `Keytop 原文负载日志不脱敏也不截断`() {
        val logger = LoggerFactory.getLogger("logging-test") as Logger
        val rawPayload = "{\"key\":\"signature\",\"plateNo\":\"粤B12345\"}"
        val event = LoggingEvent().apply {
            this.loggerName = logger.name
            this.level = Level.INFO
            this.message = "Keytop HTTP 请求原文: body=$rawPayload"
            this.argumentArray = emptyArray()
            this.mdcPropertyMap = emptyMap()
            this.timeStamp = 0L
            addMarker(MarkerFactory.getMarker("KEYTOP_RAW_PAYLOAD"))
        }
        val layout = JsonLogLayout().apply {
            maxMessageLength = 16
            start()
        }

        try {
            val output = layout.doLayout(event)
            assertTrue("signature" in output)
            assertTrue("粤B12345" in output)
            assertFalse("[REDACTED]" in output)
            assertFalse("[truncated]" in output)
        } finally {
            layout.stop()
        }
    }
}
