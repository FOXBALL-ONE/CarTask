package top.foxball.cartask.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDateTime
import top.foxball.cartask.entity.type.ViolationType

/** 违规记录，记录违规主体、违规类型及处理结果。 */
@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(
    name = "violation_record",
    indexes = [
        // 删除违规类型前要先确认没有记录引用它。
        Index(name = "idx_violation_record_type", columnList = "violation_type_id"),
    ],
)
class ViolationRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    /** 违规主体。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "violation_subject_id", nullable = false)
    lateinit var subject: ViolationSubject

    /** 违规类型。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "violation_type_id", nullable = false)
    lateinit var violationType: ViolationType

    /** 违规发生时间。 */
    @Column(name = "violation_time", nullable = false)
    lateinit var violationTime: LocalDateTime

    /** 违规发生地点。 */
    @Column(name = "violation_location", length = 255)
    var violationLocation: String? = null

    /** 违规扣分快照，避免违规类型后续调整影响历史记录。 */
    @Column(name = "violation_fraction", nullable = false)
    var violationFraction: Int = 0

    /** 违规内容快照。 */
    @Column(name = "violation_content", length = 1024)
    var violationContent: String? = null

    /** 证据或附件地址，多个地址以 JSON 或约定格式保存。 */
    @Column(name = "evidence_info", length = 2048)
    var evidenceInfo: String? = null

    /** 处理状态。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "handling_status", nullable = false, length = 16)
    var handlingStatus: HandlingStatus = HandlingStatus.PENDING

    /** 处理时间。 */
    @Column(name = "handled_at")
    var handledAt: LocalDateTime? = null

    /** 处理人名称。 */
    @Column(name = "handler_name", length = 128)
    var handlerName: String? = null

    /** 处理备注。 */
    @Column(name = "handling_remark", length = 1024)
    var handlingRemark: String? = null

    /** 创建时间。 */
    @Column(nullable = false, updatable = false)
    lateinit var createdAt: LocalDateTime

    /** 最后更新时间。 */
    @Column(nullable = false)
    lateinit var updatedAt: LocalDateTime

    enum class HandlingStatus {
        PENDING,
        CONFIRMED,
        HANDLED,
        CANCELLED,
    }
}
