package top.foxball.cartask.entity

import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(
    name = "parking_plate",
    indexes = [
        Index(name = "idx_parking_plate_owner", columnList = "owner_id"),
        Index(name = "idx_parking_plate_linked_user", columnList = "linked_user_id"),
    ],
    uniqueConstraints = [UniqueConstraint(name = "uk_parking_plate_number", columnNames = ["plate"])],
)
class ParkingPlate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
    
    @Column(nullable = false, length = 32)
    lateinit var plate: String
    
    @Column(nullable = false, length = 128)
    lateinit var owner: String
    
    @Column(nullable = false)
    var ownerId: Long = 0
    
    
    @Column(name = "linked_user_id")
    var linkedUserId: Long? = null
    
    @Column(nullable = false)
    var status: Int = 1
    
    @Column(nullable = false)
    lateinit var regDate: LocalDate
    
    
    @Column(name = "car_brand", nullable = false, length = 64, columnDefinition = "varchar(64) default ''")
    var carBrand: String = ""
    
    
    @Column(name = "inspection_date")
    var inspectionDate: LocalDate? = null
    
    
    @Column(name = "inspection_valid_until")
    var inspectionValidUntil: LocalDate? = null
    
    
    @Column(name = "inspection_remark", length = 255)
    var inspectionRemark: String? = null

    @Column(name = "keytop_card_id")
    var keytopCardId: Long? = null

    @Column(name = "keytop_plate_id")
    var keytopPlateId: Long? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "keytop_sync_status", nullable = false, length = 24, columnDefinition = "varchar(24) default 'PENDING'")
    var keytopSyncStatus: KeytopSyncStatus = KeytopSyncStatus.PENDING

    @Column(name = "keytop_sync_version", nullable = false, columnDefinition = "bigint default 0")
    var keytopSyncVersion: Long = 0

    @Column(name = "keytop_last_synced_at")
    var keytopLastSyncedAt: LocalDateTime? = null

    @Column(name = "keytop_last_error", length = 2048)
    var keytopLastError: String? = null

    @Column(name = "keytop_sync_request_id", length = 64)
    var keytopSyncRequestId: String? = null
    
    @Column(nullable = false, updatable = false)
    lateinit var createdAt: LocalDateTime
    
    @Column(nullable = false)
    lateinit var updatedAt: LocalDateTime

    enum class KeytopSyncStatus {
        PENDING,
        PROCESSING,
        SYNCED,
        FAILED,
        BLOCKED,
        DELETE_PENDING,
        DELETED,
    }
}
