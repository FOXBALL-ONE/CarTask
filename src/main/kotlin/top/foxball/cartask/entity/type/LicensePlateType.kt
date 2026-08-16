package top.foxball.cartask.entity.type

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "license_late_type")
class LicensePlateType {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status:Status = Status.Activity
    
    @Column(name = "car_type", length = 32)
    var carType :String?=null
    
    /** 显示顺序，数值越小越靠前。 */
    @Column(name = "sort_order", nullable = false)
    var orderNumber: Int = 0
    
    /** 创建时间。 */
    @Column(nullable = false, updatable = false)
    lateinit var createdAt: LocalDateTime
    
    /** 最后更新时间。 */
    @Column(nullable = false)
    lateinit var updatedAt: LocalDateTime
    
    enum class Status {
        Activity,
        BANNED,
    }

}