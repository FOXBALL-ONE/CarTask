package top.foxball.cartask.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

/** 追加式业务与安全审计事件；应用代码不提供更新和删除接口。 */
@Entity
@Table(
    name = "audit_event",
    indexes = [
        Index(name = "idx_audit_event_occurred", columnList = "occurred_at,id"),
        Index(name = "idx_audit_event_occurred_event", columnList = "occurred_at,event_id"),
        Index(name = "idx_audit_event_recorded_sequence", columnList = "partition_key,sequence_no"),
        Index(name = "idx_audit_event_actor", columnList = "actor_user_id,occurred_at"),
        Index(name = "idx_audit_event_action", columnList = "action,occurred_at"),
        Index(name = "idx_audit_event_target", columnList = "target_type,target_id,occurred_at"),
        Index(name = "idx_audit_event_request", columnList = "request_id"),
        Index(name = "uk_audit_event_idempotency", columnList = "source_system,action,idempotency_key", unique = true),
        Index(name = "uk_audit_event_partition_sequence", columnList = "partition_key,sequence_no", unique = true),
    ],
)
class AuditEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "event_id", nullable = false, unique = true, length = 36)
    lateinit var eventId: UUID

    @Column(name = "occurred_at", nullable = false)
    lateinit var occurredAt: LocalDateTime

    @Column(name = "recorded_at", nullable = false)
    lateinit var recordedAt: LocalDateTime

    @Column(name = "request_id", length = 64)
    var requestId: String? = null

    @Column(name = "trace_id", length = 128)
    var traceId: String? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 16)
    lateinit var actorType: ActorType

    @Column(name = "actor_user_id")
    var actorUserId: Long? = null

    @Column(name = "actor_username", nullable = false, length = 128)
    lateinit var actorUsername: String

    @Column(name = "actor_role", length = 64)
    var actorRole: String? = null

    /** JSON 数组字符串；不保存 JWT 或凭据。 */
    @Column(name = "authorities", columnDefinition = "TEXT")
    var authorities: String? = null

    @Column(nullable = false, length = 128)
    lateinit var action: String

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    lateinit var category: Category

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 16)
    lateinit var riskLevel: RiskLevel

    @Column(name = "target_type", nullable = false, length = 64)
    lateinit var targetType: String

    @Column(name = "target_id", length = 128)
    var targetId: String? = null

    @Column(name = "target_summary", columnDefinition = "TEXT")
    var targetSummary: String? = null

    @Column(name = "scope_summary", columnDefinition = "TEXT")
    var scopeSummary: String? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    lateinit var result: Result

    @Column(name = "reason_code", length = 128)
    var reasonCode: String? = null

    @Column(length = 1024)
    var reason: String? = null

    @Column(name = "before_data", columnDefinition = "TEXT")
    var beforeData: String? = null

    @Column(name = "after_data", columnDefinition = "TEXT")
    var afterData: String? = null

    @Column(name = "source_ip", length = 64)
    var sourceIp: String? = null

    @Column(name = "user_agent", length = 512)
    var userAgent: String? = null

    @Column(name = "source_system", nullable = false, length = 32)
    lateinit var sourceSystem: String

    @Column(name = "partition_key", nullable = false, length = 16)
    lateinit var partitionKey: String

    @Column(name = "sequence_no", nullable = false)
    var sequenceNo: Long = 0

    @Column(name = "previous_hash", length = 64)
    var previousHash: String? = null

    @Column(name = "event_hash", nullable = false, length = 64)
    lateinit var eventHash: String

    @Column(name = "idempotency_key", length = 256)
    var idempotencyKey: String? = null

    enum class ActorType { USER, SYSTEM, EXTERNAL, ANONYMOUS }
    enum class Category {
        AUTHENTICATION,
        AUTHORIZATION,
        ACCOUNT,
        ACCESS_CONTROL,
        ACCESS_RECORD,
        DEVICE,
        FILE,
        CONFIGURATION,
        DATA_EXPORT,
    }
    enum class RiskLevel { LOW, MEDIUM, HIGH, CRITICAL }
    enum class Result { SUCCESS, DENIED, FAILED }
}
