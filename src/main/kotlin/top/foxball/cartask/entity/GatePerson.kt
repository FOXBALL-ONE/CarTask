package top.foxball.cartask.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import top.foxball.cartask.scope.DepartmentScoped
import jakarta.persistence.UniqueConstraint
import com.fasterxml.jackson.annotation.JsonValue
import java.time.LocalDateTime

@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(
    name = "gate_person",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_gate_person_code", columnNames = ["code"]),
        UniqueConstraint(name = "uk_gate_person_id_card", columnNames = ["id_card"]),
    ],
)
class GatePerson : DepartmentScoped {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(nullable = false, length = 64)
    lateinit var code: String

    @Column(nullable = false, length = 128)
    lateinit var dept: String

    /** 部门编码，取值来自 [Department.departmentNumber]；历史数据为 null，读时按 [dept] 兜底解析。 */
    @Column(name = "department_code", length = 64)
    var departmentCode: String? = null

    @Column(nullable = false, length = 128)
    lateinit var name: String

    @Column(nullable = false, length = 32)
    lateinit var phone: String

    @Column(name = "id_card", nullable = false, length = 32)
    lateinit var idCard: String

    @Column(length = 1024)
    var face: String? = null

    @Column(nullable = false, updatable = false)
    lateinit var createTime: LocalDateTime

    @Enumerated(EnumType.STRING)
    @Column(name = "approve_status", nullable = false, length = 16)
    var approveStatus: ApproveStatus = ApproveStatus.PENDING

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status", nullable = false, length = 16)
    var syncStatus: SyncStatus = SyncStatus.NOT_SYNCED

    @Column(nullable = false)
    lateinit var updatedAt: LocalDateTime

    override val scopeDeptFreeText: String? get() = dept

    override val scopeDeptCode: String? get() = departmentCode

    enum class ApproveStatus {
        PENDING, APPROVED, REJECTED;
        @JsonValue fun value(): String = when (this) { PENDING -> "审核中"; APPROVED -> "通过"; REJECTED -> "拒绝" }
    }
    enum class SyncStatus {
        SYNCED, NOT_SYNCED;
        @JsonValue fun value(): String = if (this == SYNCED) "已同步" else "未同步"
    }
}
