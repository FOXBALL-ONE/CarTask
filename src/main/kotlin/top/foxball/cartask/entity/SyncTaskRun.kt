package top.foxball.cartask.entity

import jakarta.persistence.*
import java.time.LocalDateTime


@Entity
@Table(
    name = "sync_task_run",
    indexes = [
        Index(name = "idx_sync_task_run_task_started", columnList = "task_key,started_at,id"),
        Index(name = "idx_sync_task_run_started", columnList = "started_at,id"),
        Index(name = "idx_sync_task_run_request", columnList = "request_id"),
    ],
)
class SyncTaskRun {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
    
    @Column(name = "task_key", nullable = false, length = 128)
    lateinit var taskKey: String
    
    @Column(name = "task_name", nullable = false, length = 128)
    lateinit var taskName: String
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    lateinit var trigger: Trigger
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    lateinit var status: Status
    
    @Column(name = "request_id", length = 64)
    var requestId: String? = null
    
    @Column(name = "source_system", nullable = false, length = 32)
    lateinit var sourceSystem: String
    
    @Column(name = "actor_user_id")
    var actorUserId: Long? = null
    
    @Column(name = "actor_username", nullable = false, length = 128)
    lateinit var actorUsername: String
    
    @Column(name = "actor_role", length = 64)
    var actorRole: String? = null
    
    @Column(name = "started_at", nullable = false)
    lateinit var startedAt: LocalDateTime
    
    @Column(name = "finished_at", nullable = false)
    lateinit var finishedAt: LocalDateTime
    
    @Column(name = "duration_ms", nullable = false)
    var durationMs: Long = 0
    
    @Column(name = "data_start_time")
    var dataStartTime: LocalDateTime? = null
    
    @Column(name = "data_end_time")
    var dataEndTime: LocalDateTime? = null
    
    @Column(name = "processed_count")
    var processedCount: Int? = null
    
    @Column(name = "local_photo_count")
    var localPhotoCount: Int? = null
    
    @Column(name = "failed_photo_count")
    var failedPhotoCount: Int? = null
    
    @Column(columnDefinition = "TEXT")
    var summary: String? = null
    
    @Column(columnDefinition = "TEXT")
    var error: String? = null
    
    enum class Trigger { SCHEDULED, MANUAL }
    enum class Status { SUCCESS, FAILED }
}
