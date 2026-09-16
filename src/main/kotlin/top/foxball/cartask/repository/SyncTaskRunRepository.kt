package top.foxball.cartask.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import top.foxball.cartask.entity.SyncTaskRun

interface SyncTaskRunRepository : JpaRepository<SyncTaskRun, Long> {
    
    
    fun findAllByTaskKeyOrderByStartedAtDescIdDesc(taskKey: String): List<SyncTaskRun>
    
    
    fun findByTaskKeyOrderByStartedAtDescIdDesc(taskKey: String, pageable: Pageable): Page<SyncTaskRun>
    
    
    fun findByOrderByStartedAtDescIdDesc(pageable: Pageable): Page<SyncTaskRun>
}
