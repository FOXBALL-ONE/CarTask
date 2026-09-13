package top.foxball.cartask.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * 某个同步任务被改过的执行周期。
 *
 * 只存覆盖值，不存默认值：默认 cron 来自配置文件，没有对应行时按配置生效。
 * 这样调整默认值（改环境变量）不需要迁移数据，也不会被一条历史行悄悄压回去。
 */
@Entity
@Table(name = "sync_schedule")
class SyncSchedule {
    /** 与 [SyncTaskRun.taskKey] 同一套任务标识，页面上要按它把周期和执行历史对上。 */
    @Id
    @Column(name = "task_key", length = 128)
    lateinit var taskKey: String

    /** Spring 六段式 cron（秒 分 时 日 月 周）。 */
    @Column(name = "cron_expression", nullable = false, length = 128)
    lateinit var cronExpression: String

    @Column(name = "updated_at", nullable = false)
    lateinit var updatedAt: LocalDateTime

    @Column(name = "updated_by_user_id")
    var updatedByUserId: Long? = null

    @Column(name = "updated_by_username", length = 128)
    var updatedByUsername: String? = null
}
