package top.foxball.cartask.entity

import jakarta.persistence.*
import top.foxball.cartask.entity.type.CarType
import top.foxball.cartask.entity.type.LicensePlateType
import java.time.LocalDateTime


@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(name = "car_master_info")
class CarMasterInfo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
    
    
    @Column(name = "car_master_name", nullable = false, length = 128)
    lateinit var carMasterName: String
    
    
    @Column(name = "car_master_phone", length = 32)
    var carMasterPhone: String? = null
    
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    var department: Department? = null
    
    
    @Column(name = "link_address", length = 255)
    var linkAddress: String? = null
    
    
    @Column(name = "car_card_number", length = 64)
    var carCardNumber: String? = null
    
    
    @Column(name = "assistant_info", length = 255)
    var assistantInfo: String? = null
    
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "car_type_id")
    var carType: CarType? = null
    
    
    @Column(name = "updated_at", nullable = false)
    lateinit var updateTime: LocalDateTime
    
    
    @Column(name = "end_at")
    var endTime: LocalDateTime? = null
    
    
    @Column(name = "location_info", length = 255)
    var locationInfo: String? = null
    
    
    @ElementCollection
    @CollectionTable(
        name = "car_master_parking_spaces",
        joinColumns = [JoinColumn(name = "car_master_id")],
    )
    @OrderColumn(name = "item_order")
    var parkingSpaces: MutableList<ParkingSpaceItem> = mutableListOf()
    
    
    @ElementCollection
    @CollectionTable(
        name = "car_master_cards",
        joinColumns = [JoinColumn(name = "car_master_id")],
    )
    @OrderColumn(name = "item_order")
    var cards: MutableList<CarCardItem> = mutableListOf()
    
    
    @Embeddable
    class ParkingSpaceItem {
        
        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "license_plate_type_id")
        var licensePlateType: LicensePlateType? = null
        
        
        @Column(name = "parking_space_count", nullable = false)
        var parkingSpaceCount: Int = 0
        
        
        @Column(name = "location_info", length = 255)
        var locationInfo: String? = null
    }
    
    
    @Embeddable
    class CarCardItem {
        
        @Column(name = "car_number", length = 64)
        var carNumber: String? = null
        
        
        @Column(name = "entry_exit_voucher", length = 128)
        var entryExitVoucher: String? = null
        
        
        @Column(nullable = false)
        var status: Boolean = true
        
        
        @Column(length = 255)
        var remark: String? = null
    }
}
