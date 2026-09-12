package top.foxball.cartask.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(name = "parking_plate", uniqueConstraints = [UniqueConstraint(name = "uk_parking_plate_number", columnNames = ["plate"])])
class ParkingPlate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(nullable = false, length = 32)
    lateinit var plate: String

    @Column(nullable = false, length = 128)
    lateinit var owner: String

    @Column(nullable = false)
    var ownerId: Long = 0

    /**
     * 显式指定的归属账号。设置后优先于通过车主手机号自动关联，用于公司车、共用车辆等场景。
     * 裸 id，不加外键约束，与 [ownerId] 保持一致。
     */
    @Column(name = "linked_user_id")
    var linkedUserId: Long? = null

    @Column(nullable = false)
    var status: Int = 1

    @Column(nullable = false)
    lateinit var regDate: LocalDate

    @Column(nullable = false, updatable = false)
    lateinit var createdAt: LocalDateTime

    @Column(nullable = false)
    lateinit var updatedAt: LocalDateTime
}
