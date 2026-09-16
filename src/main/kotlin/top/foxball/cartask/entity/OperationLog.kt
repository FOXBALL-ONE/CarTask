package top.foxball.cartask.entity

import jakarta.persistence.*
import java.time.LocalDateTime


@Entity
@Table(
    name = "operation_log",
    indexes = [
        Index(name = "idx_operation_log_occurred", columnList = "occurred_at,id"),
        Index(name = "idx_operation_log_actor", columnList = "actor_user_id,occurred_at"),
        Index(name = "idx_operation_log_path", columnList = "path,occurred_at"),
    ],
)
class OperationLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
    
    @Column(name = "request_id", nullable = false, length = 64)
    lateinit var requestId: String
    
    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 16)
    lateinit var actorType: AuditEvent.ActorType
    
    @Column(name = "actor_user_id")
    var actorUserId: Long? = null
    
    @Column(name = "actor_username", nullable = false, length = 128)
    lateinit var actorUsername: String
    
    @Column(name = "actor_role", length = 64)
    var actorRole: String? = null
    
    @Column(nullable = false, length = 16)
    lateinit var method: String
    
    @Column(nullable = false, length = 512)
    lateinit var path: String
    
    @Column(name = "status_code", nullable = false)
    var statusCode: Int = 200
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    lateinit var result: Result
    
    @Column(name = "occurred_at", nullable = false)
    lateinit var occurredAt: LocalDateTime
    
    @Column(name = "duration_ms", nullable = false)
    var durationMs: Long = 0
    
    @Column(name = "source_ip", length = 64)
    var sourceIp: String? = null
    
    @Column(name = "user_agent", length = 512)
    var userAgent: String? = null
    
    @Column(length = 512)
    var error: String? = null
    
    enum class Result { SUCCESS, DENIED, FAILED }
}
