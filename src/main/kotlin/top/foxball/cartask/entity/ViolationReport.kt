package top.foxball.cartask.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(
    name = "violation_report",
    indexes = [Index(name = "idx_violation_report_reporter_created", columnList = "reporter_user_id,created_at")],
)
class ViolationReport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
    
    @Column(name = "reporter_user_id", nullable = false)
    var reporterUserId: Long = 0
    
    @Column(name = "vehicle_number", nullable = false, length = 64)
    lateinit var vehicleNumber: String
    
    @Column(name = "violation_type", nullable = false, length = 64)
    lateinit var violationType: String
    
    @Column(name = "violation_time", nullable = false)
    lateinit var violationTime: LocalDateTime
    
    @Column(name = "violation_location", length = 255)
    var violationLocation: String? = null
    
    @Column(name = "violation_content", nullable = false, length = 1024)
    lateinit var violationContent: String
    
    @Column(name = "evidence_info", length = 2048)
    var evidenceInfo: String? = null
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: Status = Status.PENDING
    
    @Column(name = "handling_remark", length = 1024)
    var handlingRemark: String? = null
    
    @Column(name = "handled_at")
    var handledAt: LocalDateTime? = null
    
    @Column(nullable = false, updatable = false)
    lateinit var createdAt: LocalDateTime
    
    @Column(nullable = false)
    lateinit var updatedAt: LocalDateTime
    
    enum class Status {
        PENDING,
        ACCEPTED,
        RESOLVED,
        REJECTED,
    }
}
