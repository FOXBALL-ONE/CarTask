package top.foxball.cartask.entity

import jakarta.persistence.*
import java.time.LocalDateTime

/** 停车场详情，由科拓停车区域同步任务维护。 */
@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(name = "parking_lot")
class ParkingLot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    /** 科拓平台车场编号，用于同步幂等匹配。 */
    @Column(name = "park_code", length = 64, unique = true)
    var parkCode: String? = null

    /** 停车场名称，优先取配置 keytop.park-name，未配置时使用车场编号。 */
    @Column(nullable = false, length = 128)
    var name: String = ""

    /** 科拓平台返回的车场总车位数量。 */
    @Column(name = "total_place_count", nullable = false)
    var totalPlaceCount: Int = 0

    /** 最近一次同步到的区域数量。 */
    @Column(name = "area_count", nullable = false)
    var areaCount: Int = 0

    /** 科拓原始返回的 parkArea 字段，含义未确认，原样保存。 */
    @Column(name = "park_area", length = 64)
    var parkArea: String? = null

    /** 创建时间。 */
    @Column(nullable = false, updatable = false)
    lateinit var createdAt: LocalDateTime

    /** 最后更新时间。 */
    @Column(nullable = false)
    lateinit var updatedAt: LocalDateTime
}
