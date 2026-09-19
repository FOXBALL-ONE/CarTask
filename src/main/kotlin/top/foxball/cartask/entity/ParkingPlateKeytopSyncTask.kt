package top.foxball.cartask.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(
    name = "parking_plate_keytop_sync_task",
    indexes = [
        Index(name = "idx_plate_keytop_sync_due", columnList = "status,next_attempt_at,id"),
        Index(name = "idx_plate_keytop_sync_plate_version", columnList = "plate_id,version"),
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uk_plate_keytop_sync_idempotency", columnNames = ["idempotency_key"]),
    ],
)
class ParkingPlateKeytopSyncTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "plate_id")
    var plateId: Long? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    lateinit var operation: Operation

    @Column(nullable = false)
    var version: Long = 0

    @Column(name = "plate_no", nullable = false, length = 32)
    lateinit var plateNo: String

    @Column(name = "old_plate_no", length = 32)
    var oldPlateNo: String? = null

    @Column(name = "owner_name", nullable = false, length = 128)
    lateinit var ownerName: String

    @Column(name = "owner_phone", nullable = false, length = 32)
    lateinit var ownerPhone: String

    @Column(name = "department_code", length = 64)
    var departmentCode: String? = null

    @Column(name = "keytop_card_id")
    var keytopCardId: Long? = null

    @Column(name = "keytop_plate_id")
    var keytopPlateId: Long? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, columnDefinition = "varchar(16) default 'PENDING'")
    var status: Status = Status.PENDING

    @Column(nullable = false)
    var attempts: Int = 0

    @Column(name = "next_attempt_at")
    var nextAttemptAt: LocalDateTime? = null

    @Column(name = "processing_started_at")
    var processingStartedAt: LocalDateTime? = null

    @Column(name = "last_error", length = 2048)
    var lastError: String? = null

    @Column(name = "last_request_id", length = 64)
    var lastRequestId: String? = null

    @Column(name = "idempotency_key", nullable = false, length = 128)
    lateinit var idempotencyKey: String

    @Column(nullable = false, updatable = false)
    lateinit var createdAt: LocalDateTime

    @Column(nullable = false)
    lateinit var updatedAt: LocalDateTime

    enum class Operation { UPSERT, DELETE }
    enum class Status { PENDING, PROCESSING, SUCCEEDED, RETRYING, DEAD, BLOCKED }
}
