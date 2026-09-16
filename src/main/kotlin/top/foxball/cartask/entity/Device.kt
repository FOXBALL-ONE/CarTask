package top.foxball.cartask.entity

import jakarta.persistence.*
import java.time.LocalDateTime


@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(name = "device")
class Device {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
    
    
    @Column(name = "device_name", length = 64)
    var deviceName: String? = null
    
    
    @Column(name = "device_code", unique = true, length = 64)
    var deviceCode: String? = null
    
    
    @Column(name = "device_type", length = 32)
    var deviceType: String? = null
    
    @Column(length = 64)
    var brand: String? = null
    
    @Column(length = 64)
    var model: String? = null
    
    @Column(length = 255)
    var location: String? = null
    
    @Column(name = "ip_address", length = 64)
    var ip: String? = null
    
    @Column(name = "install_date", length = 32)
    var installDate: String? = null
    
    
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
