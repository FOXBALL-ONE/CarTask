package top.foxball.cartask.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime


@Entity
@Table(name = "sync_schedule")
class SyncSchedule {
    
    @Id
    @Column(name = "task_key", length = 128)
    lateinit var taskKey: String
    
    
    @Column(name = "cron_expression", nullable = false, length = 128)
    lateinit var cronExpression: String
    
    @Column(name = "updated_at", nullable = false)
    lateinit var updatedAt: LocalDateTime
    
    @Column(name = "updated_by_user_id")
    var updatedByUserId: Long? = null
    
    @Column(name = "updated_by_username", length = 128)
    var updatedByUsername: String? = null
}
