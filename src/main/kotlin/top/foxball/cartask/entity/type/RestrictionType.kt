package top.foxball.cartask.entity.type

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime
import top.foxball.cartask.entity.AuditingEntityListener

/** 访问或业务限制类型字典。 */
@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(name = "restriction_type")
class RestrictionType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    /** 限制类型名称。 */
    @Column(name = "restriction_name", length = 32)
    var restrictionName: String? = null

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
