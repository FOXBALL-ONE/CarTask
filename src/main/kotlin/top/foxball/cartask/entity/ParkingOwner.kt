package top.foxball.cartask.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import top.foxball.cartask.scope.DepartmentScoped
import jakarta.persistence.UniqueConstraint
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(
    name = "parking_owner",
    indexes = [
        // 车主归属解析：手机号命中且未被显式指定给别人时算本人，见 ScopeQuerySupport.ownersOf。
        Index(name = "idx_parking_owner_phone", columnList = "phone"),
        Index(name = "idx_parking_owner_linked_user", columnList = "linked_user_id"),
    ],
    uniqueConstraints = [UniqueConstraint(name = "uk_parking_owner_card_id", columnNames = ["card_id"])],
)
class ParkingOwner : DepartmentScoped {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "card_id", nullable = false, length = 64)
    lateinit var cardId: String

    @Column(nullable = false, length = 128)
    lateinit var name: String

    @Column(nullable = false, length = 128)
    lateinit var dept: String

    @Column(nullable = false, length = 32)
    lateinit var phone: String

    /**
     * 部门编码，取值来自 [Department.departmentNumber]。
     *
     * 之所以按编码而不是名称：department 表只有 departmentNumber 唯一，name 可以重名，
     * 按名称匹配在重名与改名后都会错。历史数据为 null，读时按 [dept] 兜底解析。
     */
    @Column(name = "department_code", length = 64)
    var departmentCode: String? = null

    /**
     * 显式指定的归属账号。设置后优先于按手机号自动关联，用于共用号码、号码被回收等场景。
     * 沿用 [ParkingPlate.ownerId] 的裸 id 约定，不加外键约束。
     */
    @Column(name = "linked_user_id")
    var linkedUserId: Long? = null

    @Column(nullable = false)
    var spotCount: Int = 0

    @Column(nullable = false)
    var plateCount: Int = 0

    @Column(nullable = false, precision = 18, scale = 2)
    var balance: BigDecimal = BigDecimal.ZERO

    @Column(nullable = false)
    var status: Int = 1

    @Column(nullable = false, updatable = false)
    lateinit var createdAt: LocalDateTime

    @Column(nullable = false)
    lateinit var updatedAt: LocalDateTime

    override val scopeDeptFreeText: String? get() = dept

    override val scopeDeptCode: String? get() = departmentCode
}
