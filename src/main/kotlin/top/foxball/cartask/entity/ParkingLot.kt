package top.foxball.cartask.entity

import jakarta.persistence.*
import java.time.LocalDateTime


@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(name = "parking_lot")
class ParkingLot {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
    
    
    @Column(name = "park_code", length = 64, unique = true)
    var parkCode: String? = null
    
    
    @Column(nullable = false, length = 128)
    var name: String = ""
    
    
    @Column(name = "total_place_count", nullable = false)
    var totalPlaceCount: Int = 0
    
    
    @Column(name = "area_count", nullable = false)
    var areaCount: Int = 0
    
    
    @Column(name = "park_area", length = 64)
    var parkArea: String? = null
    
    
    @Column(nullable = false, updatable = false)
    lateinit var createdAt: LocalDateTime
    
    
    @Column(nullable = false)
    lateinit var updatedAt: LocalDateTime
}
