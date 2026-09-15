package top.foxball.cartask.entity

import jakarta.persistence.*
import java.time.LocalDateTime

/** 违规计分与处罚规则的全局设置。 */
@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(name = "violation_setting")
class ViolationSetting {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    /** 累计分值达到该值时进入处罚名单。 */
    @Column(name = "score_threshold", nullable = false)
    var scoreThreshold: Int = 12

    /** 处罚持续天数。 */
    @Column(name = "punishment_days", nullable = false)
    var punishmentDays: Int = 30

    @Column(nullable = false, updatable = false)
    lateinit var createdAt: LocalDateTime

    @Column(nullable = false)
    lateinit var updatedAt: LocalDateTime
}
