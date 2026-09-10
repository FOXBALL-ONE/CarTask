package top.foxball.cartask.logging

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import top.foxball.cartask.entity.AuditEvent
import top.foxball.cartask.entity.OperationLog
import top.foxball.cartask.repository.OperationLogRepository
import java.time.LocalDateTime

class OperationLogServiceTests {
    private val repository = mock<OperationLogRepository>()
    private val service = OperationLogService(repository)

    @Test
    fun `操作快照保留操作者并清理边界字段`() {
        val captor = argumentCaptor<OperationLog>()
        whenever(repository.save(captor.capture())).thenAnswer { captor.firstValue }

        service.recordAsync(
            OperationLogCommand(
                requestId = "request-1",
                actorType = AuditEvent.ActorType.USER,
                actorUserId = 7,
                actorUsername = "alice\n",
                actorRole = "ADMIN\t",
                method = "post",
                path = "/api/users\u0000/7",
                statusCode = 201,
                result = OperationLog.Result.SUCCESS,
                occurredAt = LocalDateTime.of(2026, 9, 10, 8, 0),
                durationMs = 12,
                sourceIp = "127.0.0.1",
                userAgent = "browser",
                error = null,
            ),
        )

        val saved = captor.firstValue
        assertEquals(7, saved.actorUserId)
        assertEquals("alice", saved.actorUsername)
        assertEquals("ADMIN", saved.actorRole)
        assertEquals("POST", saved.method)
        assertFalse(saved.path.contains('\u0000'))
        assertEquals(201, saved.statusCode)
        assertEquals(12, saved.durationMs)
    }

    @Test
    fun `数据库写入异常不会向请求线程抛出`() {
        whenever(repository.save(any())).thenThrow(IllegalStateException("db unavailable"))

        service.recordAsync(
            OperationLogCommand(
                requestId = "request-2",
                actorType = AuditEvent.ActorType.ANONYMOUS,
                actorUserId = null,
                actorUsername = "anonymous",
                actorRole = null,
                method = "GET",
                path = "/api/health",
                statusCode = 503,
                result = OperationLog.Result.FAILED,
                occurredAt = LocalDateTime.now(),
                durationMs = 1,
                sourceIp = null,
                userAgent = null,
                error = "IllegalStateException",
            ),
        )
    }
}
