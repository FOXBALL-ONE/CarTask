package top.foxball.cartask.entity

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Embeddable
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OrderColumn
import jakarta.persistence.Table
import java.time.LocalDateTime
import top.foxball.cartask.entity.type.CarType
import top.foxball.cartask.entity.type.LicensePlateType

/** 车辆主档，保存车辆联系人、归属和通行卡等基础信息。 */
@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(name = "car_master_info")
class CarMasterInfo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    /** 车辆主档名称。 */
    @Column(name = "car_master_name", nullable = false, length = 128)
    lateinit var carMasterName: String

    /** 车辆主档联系人电话。 */
    @Column(name = "car_master_phone", length = 32)
    var carMasterPhone: String? = null

    /** 车辆所属部门。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    var department: Department? = null

    /** 联系地址。 */
    @Column(name = "link_address", length = 255)
    var linkAddress: String? = null

    /** 主卡编号。 */
    @Column(name = "car_card_number", length = 64)
    var carCardNumber: String? = null

    /** 车辆助理信息，例如助理姓名或联系方式。 */
    @Column(name = "assistant_info", length = 255)
    var assistantInfo: String? = null

    /** 车辆类型。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "car_type_id")
    var carType: CarType? = null

    /** 车辆档案最后更新时间。 */
    @Column(name = "updated_at", nullable = false)
    lateinit var updateTime: LocalDateTime

    /** 车辆档案有效期结束时间；为空表示长期有效。 */
    @Column(name = "end_at")
    var endTime: LocalDateTime? = null

    /** 车辆所在位置或停车区域说明。 */
    @Column(name = "location_info", length = 255)
    var locationInfo: String? = null

    /** 车辆可用的停车位明细。 */
    @ElementCollection
    @CollectionTable(
        name = "car_master_parking_spaces",
        joinColumns = [JoinColumn(name = "car_master_id")],
    )
    @OrderColumn(name = "item_order")
    var parkingSpaces: MutableList<ParkingSpaceItem> = mutableListOf()

    /** 车辆通行卡明细。 */
    @ElementCollection
    @CollectionTable(
        name = "car_master_cards",
        joinColumns = [JoinColumn(name = "car_master_id")],
    )
    @OrderColumn(name = "item_order")
    var cards: MutableList<CarCardItem> = mutableListOf()

    /** 停车位明细。 */
    @Embeddable
    class ParkingSpaceItem {
        /** 车牌类型。 */
        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "license_plate_type_id")
        var licensePlateType: LicensePlateType? = null

        /** 可用停车位数量。 */
        @Column(name = "parking_space_count", nullable = false)
        var parkingSpaceCount: Int = 0

        /** 停车位位置说明。 */
        @Column(name = "location_info", length = 255)
        var locationInfo: String? = null
    }

    /** 车辆通行卡明细。 */
    @Embeddable
    class CarCardItem {
        /** 车辆编号或车牌号。 */
        @Column(name = "car_number", length = 64)
        var carNumber: String? = null

        /** 进出场凭证编号。 */
        @Column(name = "entry_exit_voucher", length = 128)
        var entryExitVoucher: String? = null

        /** 卡片是否有效。 */
        @Column(nullable = false)
        var status: Boolean = true

        /** 卡片备注。 */
        @Column(length = 255)
        var remark: String? = null
    }
}
