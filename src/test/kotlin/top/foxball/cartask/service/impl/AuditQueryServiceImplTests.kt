package top.foxball.cartask.service.impl

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import top.foxball.cartask.audit.AuditService
import top.foxball.cartask.entity.AuditEvent
import top.foxball.cartask.repository.AuditEventRepository
import top.foxball.cartask.service.AuditQueryService
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.LocalDateTime

class AuditQueryServiceImplTests {
    private val service = AuditQueryServiceImpl(
        mock<AuditEventRepository>(),
        jacksonObjectMapper(),
        mock<AuditService>(),
    )

    @Test
    fun `查询时间范围不能超过31天`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.list(
                AuditQueryService.Query(
                    occurredFrom = LocalDateTime.of(2026, 1, 1, 0, 0),
                    occurredTo = LocalDateTime.of(2026, 2, 2, 0, 0),
                ),
            )
        }
    }

    @Test
    fun `导出必须限定在7天内`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.export(
                AuditQueryService.Query(
                    occurredFrom = LocalDateTime.of(2026, 1, 1, 0, 0),
                    occurredTo = LocalDateTime.of(2026, 1, 9, 0, 0),
                ),
            )
        }
    }

    @Test
    fun `导出必须提供时间范围`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.export(AuditQueryService.Query())
        }
    }

    @Test
    fun `哈希链首条事件必须从序号1开始`() {
        val repository = mock<AuditEventRepository>()
        val service = AuditQueryServiceImpl(repository, jacksonObjectMapper(), mock<AuditService>())
        val first = AuditEvent().apply {
            partitionKey = "2026-08"
            sequenceNo = 3
            previousHash = "unexpected"
        }
        whenever(repository.findByPartitionKeyOrderBySequenceNoAsc("2026-08")).thenReturn(listOf(first))

        assertFalse(service.verify("2026-08").valid)
    }

    @Test
    fun `校验拒绝不存在的月份`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.verify("2026-13")
        }
    }

    @Test
    fun `查询拒绝过深页码`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.list(AuditQueryService.Query(page = 10_001))
        }
    }

    @Test
    fun `查询必须指定时间范围`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.list(AuditQueryService.Query())
        }
    }

    @Test
    fun `查询拒绝非正操作者 ID`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.list(
                AuditQueryService.Query(
                    occurredFrom = LocalDateTime.of(2026, 8, 1, 0, 0),
                    occurredTo = LocalDateTime.of(2026, 8, 2, 0, 0),
                    actorUserId = 0,
                ),
            )
        }
    }

    @Test
    fun `校验拒绝事件分区字段不一致`() {
        val repository = mock<AuditEventRepository>()
        val service = AuditQueryServiceImpl(repository, jacksonObjectMapper(), mock<AuditService>())
        val event = AuditEvent().apply {
            partitionKey = "2026-07"
            sequenceNo = 1
            eventHash = "invalid"
        }
        whenever(repository.findByPartitionKeyOrderBySequenceNoAsc("2026-08")).thenReturn(listOf(event))

        assertFalse(service.verify("2026-08").valid)
    }
}
