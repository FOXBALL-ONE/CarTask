package top.foxball.cartask.audit

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import tools.jackson.module.kotlin.jacksonObjectMapper
import top.foxball.cartask.authentication.CurrentUserPrincipal
import top.foxball.cartask.entity.AuditEvent
import top.foxball.cartask.repository.AuditEventRepository
import java.time.LocalDateTime
import java.util.UUID

class AuditServiceImplTests {
    private val repository = mock<AuditEventRepository>()
    private val service = AuditServiceImpl(repository, jacksonObjectMapper())

    @AfterEach
    fun clearContext() = SecurityContextHolder.clearContext()

    @Test
    fun `同步写入包含操作者快照并丢弃未知字段`() {
        val principal = CurrentUserPrincipal(7, "alice", "ADMIN", "token-id", setOf("audit:read"))
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
            principal,
            null,
            listOf(SimpleGrantedAuthority("ROLE_ADMIN"), SimpleGrantedAuthority("audit:read")),
        )
        whenever(repository.findTopByPartitionKeyOrderBySequenceNoDesc(any())).thenReturn(null)
        val captor = argumentCaptor<AuditEvent>()
        whenever(repository.save(captor.capture())).thenAnswer { captor.firstValue }

        val event = service.record(
            AuditCommand(
                AuditAction.USER_UPDATED,
                "user",
                "7",
                beforeData = mapOf("role" to "USER", "unreviewed_field" to "hidden"),
                afterData = mapOf("role" to "ADMIN"),
            ),
        )

        assertEquals(7, event.actorUserId)
        assertEquals("alice", event.actorUsername)
        assertEquals(1, event.sequenceNo)
        assertFalse(event.beforeData!!.contains("unreviewed_field"))
        assertNotNull(event.eventHash)
        verify(repository).lockPartition(event.partitionKey)
    }

    @Test
    fun `重复幂等键返回已存在事件`() {
        val existing = AuditEvent().apply {
            eventId = UUID.randomUUID()
            eventHash = "hash"
        }
        whenever(repository.findBySourceSystemAndActionAndIdempotencyKey("SYSTEM", "USER_UPDATED", "external-1"))
            .thenReturn(existing)

        val result = service.record(
            AuditCommand(AuditAction.USER_UPDATED, "user", idempotencyKey = "external-1", sourceSystem = "SYSTEM"),
        )

        assertEquals(existing, result)
    }

    @Test
    fun `分区锁后再次检查幂等键`() {
        val existing = AuditEvent().apply {
            eventId = UUID.randomUUID()
            eventHash = "hash"
        }
        whenever(repository.findBySourceSystemAndActionAndIdempotencyKey("SYSTEM", "USER_UPDATED", "external-2"))
            .thenReturn(null, existing)

        val result = service.record(
            AuditCommand(AuditAction.USER_UPDATED, "user", idempotencyKey = " external-2 ", sourceSystem = "SYSTEM"),
        )

        assertEquals(existing, result)
    }

    @Test
    fun `目标类型不能为空`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.record(AuditCommand(AuditAction.USER_UPDATED, "  "))
        }
    }

    @Test
    fun `审计原因移除控制字符`() {
        whenever(repository.findTopByPartitionKeyOrderBySequenceNoDesc(any())).thenReturn(null)
        whenever(repository.save(any())).thenAnswer { it.arguments[0] as AuditEvent }

        val event = service.record(AuditCommand(AuditAction.USER_UPDATED, "user", reason = "manual\nreview"))

        assertEquals("manual review", event.reason)
    }

    @Test
    fun `链序号沿用上一条摘要`() {
        val previous = AuditEvent().apply {
            eventHash = "previous-hash"
            sequenceNo = 4
        }
        whenever(repository.findTopByPartitionKeyOrderBySequenceNoDesc(any())).thenReturn(previous)
        whenever(repository.save(any())).thenAnswer { it.arguments[0] as AuditEvent }

        val event = service.record(AuditCommand(AuditAction.USER_UPDATED, "user", "7", occurredAt = LocalDateTime.of(2026, 8, 20, 10, 0)))

        assertEquals(5, event.sequenceNo)
        assertEquals("previous-hash", event.previousHash)
    }
}
