package top.foxball.cartask.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/** 系统接入设备及其基础状态。 */
@Entity
@Table(name = "device")
class Device {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    /** 设备显示名称。 */
    @Column(name = "device_name", length = 64)
    var deviceName: String? = null

    /** 设备唯一业务编码。 */
    @Column(name = "device_code", unique = true, length = 64)
    var deviceCode: String? = null

    /** 设备类别，例如门禁、摄像头或传感器。 */
    @Column(name = "device_type", length = 32)
    var deviceType: String? = null

    /** 设备状态使用字符串保存，避免枚举顺序变化造成数据错误。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: Status = Status.Activity

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
