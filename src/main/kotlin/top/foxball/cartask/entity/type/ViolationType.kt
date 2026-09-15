package top.foxball.cartask.entity.type

import jakarta.persistence.*
import top.foxball.cartask.entity.AuditingEntityListener
import java.time.LocalDateTime

@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(name = "violation_type")
class ViolationType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    /** 违规类型名称。 */
    @Column(name = "violation_name", length = 32)
    var violationName: String? = null

    /** 该违规类型对应的扣分值。 */
    @Column(name = "violation_fraction")
    var violationFraction: Int? = null

    /** 违规说明。 */
    @Column(name = "violation_content", length = 255)
    var violationContent: String? = null

    /** 类型状态使用字符串保存，避免枚举顺序变化造成数据错误。 */
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
