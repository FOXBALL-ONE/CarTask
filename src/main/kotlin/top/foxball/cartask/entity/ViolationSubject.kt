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
import java.time.LocalDateTime

/** 违规主体档案，保存发生违规行为的人员、车辆或其他主体信息。 */
@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(name = "violation_subject")
class ViolationSubject {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    /** 主体类型。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false, length = 16)
    var subjectType: SubjectType = SubjectType.VEHICLE

    /** 主体名称，例如人员姓名或车辆联系人姓名。 */
    @Column(name = "subject_name", nullable = false, length = 128)
    lateinit var subjectName: String

    /** 主体唯一识别号，例如车牌号、工号或证件号。 */
    @Column(name = "subject_number", nullable = false, unique = true, length = 64)
    lateinit var subjectNumber: String

    /** 联系电话。 */
    @Column(length = 32)
    var phone: String? = null

    /** 备注信息。 */
    @Column(length = 255)
    var remark: String? = null

    /** 主体状态。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: Status = Status.Activity

    /** 创建时间。 */
    @Column(nullable = false, updatable = false)
    lateinit var createdAt: LocalDateTime

    /** 最后更新时间。 */
    @Column(nullable = false)
    lateinit var updatedAt: LocalDateTime

    enum class SubjectType {
        PERSON,
        VEHICLE,
        OTHER,
    }

    enum class Status {
        Activity,
        BANNED,
    }
}
