package top.foxball.cartask.entity

import jakarta.persistence.*
import top.foxball.cartask.entity.type.CarType
import top.foxball.cartask.shared.PlateNumbers
import java.math.BigDecimal
import java.time.LocalDateTime


@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(
    name = "access_record",
    indexes = [
        Index(name = "idx_access_record_in_out_time", columnList = "in_and_out_time,id"),
        Index(name = "idx_access_record_plate_in_out_time", columnList = "car_number,in_and_out,in_and_out_time"),
        Index(name = "idx_access_record_norm_in_out_time", columnList = "car_number_normalized,in_and_out_time,id"),
        Index(name = "idx_access_record_dept_in_out_time", columnList = "department_name,in_and_out_time,id"),
        Index(name = "idx_access_record_photo_status", columnList = "photo_sync_status,id"),
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uk_access_record_source_record_id", columnNames = ["source_record_id"]),
    ],
)
class AccessRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
    
    
    @Column(name = "car_number", length = 64)
    var carNumber: String? = null
    
    
    @Column(name = "car_number_normalized", length = 64)
    var carNumberNormalized: String? = null
    
    
    @Column(name = "source_record_id", length = 256)
    var sourceRecordId: String? = null
    
    
    @Column(name = "department_name", length = 128)
    var departmentName: String? = null
    
    
    @Enumerated(EnumType.STRING)
    @Column(name = "in_and_out", nullable = false, length = 8)
    var inAndOut: InAndOut = InAndOut.IN
    
    
    @Column(name = "in_and_out_time", nullable = false)
    lateinit var inAndOutTime: LocalDateTime
    
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "car_type_id")
    var carType: CarType? = null
    
    
    @Column(name = "vehicle_type_name", length = 64)
    var vehicleTypeName: String? = null
    
    
    @Column(name = "admission_ticket_number", length = 128)
    var admissionTicketNumber: String? = null
    
    
    @Column(name = "pass_type", length = 64)
    var passType: String? = null
    
    
    @Column(name = "release_instructions", length = 512)
    var releaseInstructions: String? = null
    
    
    @Enumerated(EnumType.STRING)
    @Column(name = "release_channel", length = 16)
    var releaseChannel: ReleaseChannel? = null
    
    
    @Column(name = "operator_name", length = 128)
    var operatorName: String? = null
    
    
    @Column(name = "car_owner_name", length = 128)
    var carOwnerName: String? = null
    
    
    @Column(name = "gate_name", length = 128)
    var gateName: String? = null
    
    
    @Column(name = "photo_url", length = 1024)
    var photoUrl: String? = null
    
    
    @Column(name = "source_photo_url", length = 1024)
    var sourcePhotoUrl: String? = null
    
    
    @Enumerated(EnumType.STRING)
    @Column(name = "photo_sync_status", length = 16)
    var photoSyncStatus: PhotoSyncStatus? = null
    
    
    @Column(name = "photo_sync_error", length = 2048)
    var photoSyncError: String? = null
    
    
    @Column(name = "fee_amount", nullable = false, precision = 18, scale = 2)
    var feeAmount: BigDecimal = BigDecimal.ZERO
    
    
    @Column(name = "record_status", nullable = false, length = 16)
    var recordStatus: String = "正常"
    
    
    @Column(nullable = false, updatable = false)
    lateinit var createdAt: LocalDateTime
    
    
    enum class InAndOut {
        IN,
        OUT,
    }
    
    
    @PrePersist
    @PreUpdate
    fun syncCarNumberNormalized() {
        carNumberNormalized = PlateNumbers.normalize(carNumber)
    }
    
    
    enum class ReleaseChannel {
        AUTOMATIC,
        MANUAL,
        REMOTE,
        UNKNOWN,
    }
    
    enum class PhotoSyncStatus {
        LOCAL,
        FAILED,
        NOT_AVAILABLE,
    }
    
    companion object {
        
        fun displayVehicleTypeName(raw: String?): String? = when (raw) {
            null -> null
            "0" -> "临时车"
            "1" -> "月卡车"
            "2" -> "储值车"
            "3" -> "免费车"
            else -> raw
        }
    }
}
