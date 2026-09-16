package top.foxball.cartask.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(
    name = "parking_spot",
    uniqueConstraints = [UniqueConstraint(name = "uk_parking_spot_code", columnNames = ["code"])]
)
class ParkingSpot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
    
    @Column(nullable = false, length = 64)
    lateinit var code: String
    
    @Column(nullable = false, length = 128)
    lateinit var area: String
    
    @Column(nullable = false, length = 64)
    lateinit var type: String
    
    @Column(length = 128)
    var owner: String? = null
    
    
    @Column(name = "owner_code", length = 64)
    var ownerCode: String? = null
    
    @Column(nullable = false)
    var status: Int = 0
    
    @Column(length = 512)
    var remark: String? = null
    
    @Column(nullable = false, updatable = false)
    lateinit var createdAt: LocalDateTime
    
    @Column(nullable = false)
    lateinit var updatedAt: LocalDateTime
}
