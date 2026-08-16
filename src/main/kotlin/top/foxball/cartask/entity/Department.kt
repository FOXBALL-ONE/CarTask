package top.foxball.cartask.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

/** 组织部门，支持通过 [superior] 建立部门树。 */
@Entity
@Table(name = "department")
class Department {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    /** 部门显示名称。 */
    @Column(nullable = false, length = 128)
    lateinit var name: String

    /** 部门编码，用于系统内稳定识别部门。 */
    @Column(name = "department_code", nullable = false, unique = true, length = 64)
    lateinit var departmentNumber: String

    /** 上级部门；顶级部门为空。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "superior_id")
    var superior: Department? = null

    /** 同级部门的显示顺序，数值越小越靠前。 */
    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0

    /** 负责人姓名或标识。 */
    @Column(length = 128)
    var director: String? = null

    /** 部门联系电话。 */
    @Column(name = "contact_phone", length = 32)
    var contactPhone: String? = null
}
