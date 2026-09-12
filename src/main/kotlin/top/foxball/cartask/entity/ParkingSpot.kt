package top.foxball.cartask.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime

@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(name = "parking_spot", uniqueConstraints = [UniqueConstraint(name = "uk_parking_spot_code", columnNames = ["code"])])
class ParkingSpot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(nullable = false, length = 64)
    lateinit var code: String

    @Column(nullable = false, length = 128)
    lateinit var area: String

    @Column(nullable = false, length = 64)
    lateinit var type: String

    @Column(length = 128)
    var owner: String? = null

    /**
     * 车主卡号，取值来自 [ParkingOwner.cardId]（该列有唯一约束）。
     *
     * 车位此前只存车主姓名，而姓名会重名——导入代码自己都要靠
     * `count { it.name == owner } == 1` 来回避歧义。要按部门划定车位范围就必须有稳定键。
     * 历史数据为 null，读时按 [owner] 兜底解析。
     */
    @Column(name = "owner_code", length = 64)
    var ownerCode: String? = null

    @Column(nullable = false)
    var status: Int = 0

    @Column(length = 512)
    var remark: String? = null

    @Column(nullable = false, updatable = false)
    lateinit var createdAt: LocalDateTime

    @Column(nullable = false)
    lateinit var updatedAt: LocalDateTime
}
