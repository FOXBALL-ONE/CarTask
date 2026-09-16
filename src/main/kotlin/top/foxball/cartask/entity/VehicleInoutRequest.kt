package top.foxball.cartask.entity

import com.fasterxml.jackson.annotation.JsonValue
import jakarta.persistence.*
import top.foxball.cartask.scope.DepartmentScoped
import top.foxball.cartask.shared.PlateNumbers
import java.time.LocalDateTime


@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(
    name = "vehicle_inout_request",
    indexes = [
        Index(name = "idx_vehicle_inout_request_apply_time", columnList = "apply_time,id"),
        Index(name = "idx_vehicle_inout_request_status", columnList = "status,apply_time"),
        Index(name = "idx_vehicle_inout_request_sync_status", columnList = "sync_status,id"),
        Index(name = "idx_vehicle_inout_request_plate_normalized", columnList = "plate_normalized"),
    ],
)
class VehicleInoutRequest : DepartmentScoped {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
    
    
    @Column(nullable = false, length = 32)
    lateinit var plate: String
    
    
    @Column(name = "plate_normalized", length = 32)
    var plateNormalized: String? = null
    
    
    @Column(name = "plate_id", nullable = false)
    var plateId: Long = 0
    
    
    @Column(name = "owner_id", nullable = false)
    var ownerId: Long = 0
    
    
    @Column(nullable = false, length = 128)
    lateinit var owner: String
    
    
    @Column(nullable = false, length = 32)
    lateinit var phone: String
    
    
    @Column(name = "department_code", length = 64)
    var departmentCode: String? = null
    
    
    @Column(nullable = false, length = 128)
    lateinit var dept: String
    
    
    @Column(name = "card_name", nullable = false, length = 64)
    lateinit var cardName: String
    
    
    @Column(name = "area_code", length = 64)
    var areaCode: String? = null
    
    
    @Column(name = "area_name", length = 128)
    var areaName: String? = null
    
    
    @Column(name = "valid_from", nullable = false)
    lateinit var validFrom: LocalDateTime
    
    
    @Column(name = "valid_to", nullable = false)
    lateinit var validTo: LocalDateTime
    
    
    @Column(name = "card_id")
    var cardId: Long? = null
    
    
    @Column(name = "card_issued", nullable = false)
    var cardIssued: Boolean = false
    
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: Status = Status.PENDING
    
    
    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status", nullable = false, length = 16)
    var syncStatus: SyncStatus = SyncStatus.NOT_SYNCED
    
    
    @Column(name = "sync_message", length = 2048)
    var syncMessage: String? = null
    
    @Column(name = "apply_time", nullable = false, updatable = false)
    lateinit var applyTime: LocalDateTime
    
    
    @Column(name = "reviewed_by", length = 128)
    var reviewedBy: String? = null
    
    @Column(name = "reviewed_at")
    var reviewedAt: LocalDateTime? = null
    
    
    @Column(name = "review_reason", length = 1024)
    var reviewReason: String? = null
    
    
    @Column(name = "synced_at")
    var syncedAt: LocalDateTime? = null
    
    @Column(nullable = false, updatable = false)
    lateinit var createdAt: LocalDateTime
    
    @Column(nullable = false)
    lateinit var updatedAt: LocalDateTime
    
    override val scopeDeptFreeText: String? get() = dept
    
    override val scopeDeptCode: String? get() = departmentCode
    
    
    @PrePersist
    @PreUpdate
    fun syncPlateNormalized() {
        plateNormalized = PlateNumbers.normalize(plate)
    }
    
    enum class Status {
        PENDING, APPROVED, REJECTED, CANCELLED;
        
        
        @JsonValue
        fun value(): String = when (this) {
            PENDING -> "待审核"
            APPROVED -> "已通过"
            REJECTED -> "已驳回"
            CANCELLED -> "已撤销"
        }
    }
    
    enum class SyncStatus {
        NOT_SYNCED, SYNCED, FAILED;
        
        @JsonValue
        fun value(): String = when (this) {
            NOT_SYNCED -> "未下发"
            SYNCED -> "已下发"
            FAILED -> "下发失败"
        }
    }
}
