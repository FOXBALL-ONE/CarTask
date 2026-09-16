package top.foxball.cartask.entity

import jakarta.persistence.*
import top.foxball.cartask.entity.type.ViolationType
import java.time.LocalDateTime


@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(
    name = "violation_record",
    indexes = [
        Index(name = "idx_violation_record_type", columnList = "violation_type_id"),
    ],
)
class ViolationRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
    
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "violation_subject_id", nullable = false)
    lateinit var subject: ViolationSubject
    
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "violation_type_id", nullable = false)
    lateinit var violationType: ViolationType
    
    
    @Column(name = "violation_time", nullable = false)
    lateinit var violationTime: LocalDateTime
    
    
    @Column(name = "violation_location", length = 255)
    var violationLocation: String? = null
    
    
    @Column(name = "violation_fraction", nullable = false)
    var violationFraction: Int = 0
    
    
    @Column(name = "violation_content", length = 1024)
    var violationContent: String? = null
    
    
    @Column(name = "evidence_info", length = 2048)
    var evidenceInfo: String? = null
    
    
    @Enumerated(EnumType.STRING)
    @Column(name = "handling_status", nullable = false, length = 16)
    var handlingStatus: HandlingStatus = HandlingStatus.PENDING
    
    
    @Column(name = "handled_at")
    var handledAt: LocalDateTime? = null
    
    
    @Column(name = "handler_name", length = 128)
    var handlerName: String? = null
    
    
    @Column(name = "handling_remark", length = 1024)
    var handlingRemark: String? = null
    
    
    @Column(nullable = false, updatable = false)
    lateinit var createdAt: LocalDateTime
    
    
    @Column(nullable = false)
    lateinit var updatedAt: LocalDateTime
    
    enum class HandlingStatus {
        PENDING,
        CONFIRMED,
        HANDLED,
        CANCELLED,
    }
}
