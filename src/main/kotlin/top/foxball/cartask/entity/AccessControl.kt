package top.foxball.cartask.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDateTime
import top.foxball.cartask.entity.type.AccessControlType

/** 门禁授权记录，描述人员在指定时间范围内的通行权限。 */
@Entity
@Table(name = "access_control")
class AccessControl {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    /** 被授权人的姓名。 */
    @Column(nullable = false, length = 128)
    lateinit var name: String

    /** 被授权人的联系电话。 */
    @Column(length = 32)
    var phone: String? = null

    /** 被授权人的业务编号。 */
    @Column(name = "person_number", unique = true, length = 64)
    var personNumber: String? = null

    /** 人脸特征或人脸档案标识，不保存原始敏感图像。 */
    @Column(name = "face_info", length = 1024)
    var faceInfo: String? = null

    /** 门禁授权类型。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "access_control_type_id")
    var accessControlPermission: AccessControlType? = null

    /** 授权开始时间。 */
    @Column(name = "starts_at")
    var upTime: LocalDateTime? = null

    /** 授权结束时间；为空表示长期有效。 */
    @Column(name = "ends_at")
    var endTime: LocalDateTime? = null

    /** 门禁范围或门禁点列表说明。 */
    @Column(name = "access_control_list", length = 1024)
    var accessControlList: String? = null

    /** 授权所属部门。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    var department: Department? = null

    /** 授权审核状态。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 16)
    var reviewStatus: ReviewStatus = ReviewStatus.PENDING

    /** 是否已同步到门禁设备。 */
    @Column(name = "synchronized_loading", nullable = false)
    var synchronizedLoading: Boolean = false

    /** 记录创建时间。 */
    @Column(nullable = false, updatable = false)
    lateinit var createdAt: LocalDateTime

    /** 记录最后更新时间。 */
    @Column(nullable = false)
    lateinit var updatedAt: LocalDateTime

    enum class ReviewStatus {
        PENDING,
        APPROVED,
        REJECTED,
    }
}
