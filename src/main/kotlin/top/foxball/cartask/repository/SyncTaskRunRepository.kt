package top.foxball.cartask.repository

import org.springframework.data.jpa.repository.JpaRepository
import top.foxball.cartask.entity.SyncTaskRun

interface SyncTaskRunRepository : JpaRepository<SyncTaskRun, Long> {
    fun findAllByTaskKeyOrderByStartedAtDescIdDesc(taskKey: String): List<SyncTaskRun>
}
