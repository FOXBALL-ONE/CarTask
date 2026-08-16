package top.foxball.cartask.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/** 组织中的职位定义，可被多个用户复用。 */
@Entity
@Table(name = "position")
class Position {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    /** 职位显示名称。 */
    @Column(nullable = false, length = 128)
    lateinit var name: String

    /** 职位的稳定业务编码。 */
    @Column(name = "position_code", nullable = false, unique = true, length = 64)
    lateinit var codeNumber: String

    /** 职位在列表中的显示顺序，数值越小越靠前。 */
    @Column(name = "sort_order", nullable = false)
    var orderNumber: Int = 0

    /** 职位状态使用字符串保存，避免枚举顺序变化造成数据错误。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: Status = Status.Activity

    enum class Status {
        Activity,
        BANNED,
    }
}
