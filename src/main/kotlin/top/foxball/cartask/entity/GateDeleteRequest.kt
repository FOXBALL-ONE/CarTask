package top.foxball.cartask.entity

import com.fasterxml.jackson.annotation.JsonValue
import jakarta.persistence.*
import top.foxball.cartask.scope.DepartmentScoped
import java.time.LocalDateTime

@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(name = "gate_delete_request")
class GateDeleteRequest : DepartmentScoped {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(nullable = false)
    var personId: Long = 0

    @Column(nullable = false, length = 64)
    lateinit var code: String

    @Column(nullable = false, length = 128)
    lateinit var dept: String

    /**
     * 部门编码，取值来自 [Department.departmentNumber]；历史数据为 null，读时按 [dept] 兜底解析。
     *
     * 与 [GatePerson] 同构：删除申请必须能按数据范围裁剪，否则受限的部门管理能读到所有部门的
     * 身份证与手机号，并能批准删除范围外的人员。
     */
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

    @Column(nullable = false, length = 512)
    lateinit var reason: String

    @Column(nullable = false)
    lateinit var applyTime: LocalDateTime

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: Status = Status.PENDING

    override val scopeDeptFreeText: String? get() = dept

    override val scopeDeptCode: String? get() = departmentCode

    enum class Status {
        PENDING, APPROVED, REJECTED;

        @JsonValue
        fun value(): String = when (this) {
            PENDING -> "待处理"; APPROVED -> "已同意"; REJECTED -> "已拒绝"
        }
    }
}
