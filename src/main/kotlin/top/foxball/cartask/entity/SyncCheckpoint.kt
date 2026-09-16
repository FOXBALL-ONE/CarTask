package top.foxball.cartask.entity

import jakarta.persistence.*
import java.time.LocalDateTime


@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(name = "sync_checkpoint")
class SyncCheckpoint {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
    
    @Column(name = "sync_key", nullable = false, unique = true, length = 128)
    lateinit var syncKey: String
    
    @Column(name = "cursor_time")
    var cursorTime: LocalDateTime? = null
    
    @Column(name = "cursor_external_id", length = 256)
    var cursorExternalId: String? = null
    
    @Column(name = "last_success_at")
    var lastSuccessAt: LocalDateTime? = null
    
    @Column(name = "last_batch_id", length = 64)
    var lastBatchId: String? = null
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: Status = Status.NEVER_RUN
    
    @Column(name = "last_error", length = 2048)
    var lastError: String? = null
    
    enum class Status {
        NEVER_RUN,
        RUNNING,
        SUCCESS,
        FAILED,
    }
}
