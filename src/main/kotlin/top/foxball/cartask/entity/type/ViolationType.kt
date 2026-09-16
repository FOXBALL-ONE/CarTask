package top.foxball.cartask.entity.type

import jakarta.persistence.*
import top.foxball.cartask.entity.AuditingEntityListener
import java.time.LocalDateTime

@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(name = "violation_type")
class ViolationType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
    
    
    @Column(name = "violation_name", length = 32)
    var violationName: String? = null
    
    
    @Column(name = "violation_fraction")
    var violationFraction: Int? = null
    
    
    @Column(name = "violation_content", length = 255)
    var violationContent: String? = null
    
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: Status = Status.Activity
    
    
    @Column(name = "sort_order", nullable = false)
    var orderNumber: Int = 0
    
    
    @Column(nullable = false, updatable = false)
    lateinit var createdAt: LocalDateTime
    
    
    @Column(nullable = false)
    lateinit var updatedAt: LocalDateTime
    
    enum class Status {
        Activity,
        BANNED,
    }
}
