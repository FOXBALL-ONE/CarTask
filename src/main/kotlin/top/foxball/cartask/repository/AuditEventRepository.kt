package top.foxball.cartask.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import top.foxball.cartask.entity.AuditEvent
import java.time.LocalDateTime
import java.util.UUID

interface AuditEventRepository : JpaRepository<AuditEvent, Long>, JpaSpecificationExecutor<AuditEvent> {
    @Query("SELECT pg_advisory_xact_lock(hashtextextended(:partitionKey, 0))", nativeQuery = true)
    fun lockPartition(@Param("partitionKey") partitionKey: String): Any?

    fun findByEventId(eventId: UUID): AuditEvent?

    fun findBySourceSystemAndActionAndIdempotencyKey(
        sourceSystem: String,
        action: String,
        idempotencyKey: String,
    ): AuditEvent?

    fun findTopByPartitionKeyOrderBySequenceNoDesc(partitionKey: String): AuditEvent?

    fun findByPartitionKeyOrderBySequenceNoAsc(partitionKey: String): List<AuditEvent>

    fun findByOccurredAtBetweenOrderByOccurredAtDescEventIdDesc(
        occurredFrom: LocalDateTime,
        occurredTo: LocalDateTime,
        pageable: Pageable,
    ): Page<AuditEvent>
}
