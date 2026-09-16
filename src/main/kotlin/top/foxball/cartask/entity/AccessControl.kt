package top.foxball.cartask.entity

import jakarta.persistence.*
import top.foxball.cartask.entity.type.AccessControlType
import java.time.LocalDateTime


@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(
    name = "access_control",
    indexes = [
        Index(name = "idx_access_control_department", columnList = "department_id"),
    ],
)
class AccessControl {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
    
    
    @Column(nullable = false, length = 128)
    lateinit var name: String
    
    
    @Column(length = 32)
    var phone: String? = null
    
    
    @Column(name = "person_number", unique = true, length = 64)
    var personNumber: String? = null
    
    
    @Column(name = "face_info", length = 1024)
    var faceInfo: String? = null
    
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "access_control_type_id")
    var accessControlPermission: AccessControlType? = null
    
    
    @Column(name = "starts_at")
    var upTime: LocalDateTime? = null
    
    
    @Column(name = "ends_at")
    var endTime: LocalDateTime? = null
    
    
    @Column(name = "access_control_list", length = 1024)
    var accessControlList: String? = null
    
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    var department: Department? = null
    
    
    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 16)
    var reviewStatus: ReviewStatus = ReviewStatus.PENDING
    
    
    @Column(name = "synchronized_loading", nullable = false)
    var synchronizedLoading: Boolean = false
    
    
    @Column(nullable = false, updatable = false)
    lateinit var createdAt: LocalDateTime
    
    
    @Column(nullable = false)
    lateinit var updatedAt: LocalDateTime
    
    enum class ReviewStatus {
        PENDING,
        APPROVED,
        REJECTED,
    }
}
