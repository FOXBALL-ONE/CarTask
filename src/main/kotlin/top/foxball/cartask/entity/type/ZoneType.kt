package top.foxball.cartask.entity.type

import jakarta.persistence.*
import top.foxball.cartask.entity.AuditingEntityListener
import java.time.LocalDateTime

@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(name = "zone_type")
class ZoneType {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
    
    
    @Column(name = "zone_code", length = 64, unique = true)
    var zoneCode: String? = null
    
    @Column(name = "zone_name", length = 32)
    var zoneName: String? = null
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: Status = Status.Activity
    
    
    @Column(name = "sort_order", nullable = false)
    var orderNumber: Int = 0
    
    
    @Column(name = "place_count")
    var placeCount: Int = 0
    
    
    @Column(nullable = false, updatable = false)
    lateinit var createdAt: LocalDateTime
    
    
    @Column(nullable = false)
    lateinit var updatedAt: LocalDateTime
    
    enum class Status {
        Activity,
        BANNED,
    }
}
