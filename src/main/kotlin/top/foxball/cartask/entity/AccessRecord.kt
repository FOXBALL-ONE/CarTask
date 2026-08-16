package top.foxball.cartask.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDateTime
import top.foxball.cartask.entity.type.CarType

/** 车辆进出门禁的流水记录。 */
@Entity
@Table(name = "access_record")
class AccessRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    /** 车辆号牌；无牌车辆可为空。 */
    @Column(name = "car_number", length = 64)
    var carNumber: String? = null

    /** 进场或出场方向。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "in_and_out", nullable = false, length = 8)
    var inAndOut: InAndOut = InAndOut.IN

    /** 实际进出时间。 */
    @Column(name = "in_and_out_time", nullable = false)
    lateinit var inAndOutTime: LocalDateTime

    /** 车辆类型。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "car_type_id")
    var carType: CarType? = null

    /** 无牌车辆入场时使用的票据或卡号。 */
    @Column(name = "admission_ticket_number", length = 128)
    var admissionTicketNumber: String? = null

    /** 放行指令或审核说明。 */
    @Column(name = "release_instructions", length = 512)
    var releaseInstructions: String? = null

    /** 放行渠道。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "release_channel", length = 16)
    var releaseChannel: ReleaseChannel? = null

    /** 执行放行操作的人员。 */
    @Column(name = "operator_name", length = 128)
    var operatorName: String? = null

    /** 车主姓名。 */
    @Column(name = "car_owner_name", length = 128)
    var carOwnerName: String? = null

    /** 记录创建时间。 */
    @Column(nullable = false, updatable = false)
    lateinit var createdAt: LocalDateTime

    /** 进出方向。 */
    enum class InAndOut {
        IN,
        OUT,
    }

    /** 放行来源渠道。 */
    enum class ReleaseChannel {
        AUTOMATIC,
        MANUAL,
        REMOTE,
        UNKNOWN,
    }
}
