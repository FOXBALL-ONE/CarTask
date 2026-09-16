package top.foxball.cartask.service

import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

data class SyncTaskProgress(
    val taskKey: String,
    val taskName: String,
    val running: Boolean,
    val processedCount: Int,
    val totalCount: Int?,
    val startedAt: LocalDateTime?,
)

@Service
class SyncTaskProgressService {
    private val states = ConcurrentHashMap<String, SyncTaskProgress>()
    
    
    fun start(taskKey: String, taskName: String, startedAt: LocalDateTime, totalCount: Int? = null) {
        states.compute(taskKey) { _, current ->
            current?.takeIf { it.running }
                ?: SyncTaskProgress(taskKey, taskName, true, 0, totalCount, startedAt)
        }
    }
    
    
    fun update(taskKey: String, processedCount: Int, totalCount: Int? = null) {
        states.computeIfPresent(taskKey) { _, current ->
            current.copy(
                processedCount = processedCount,
                totalCount = totalCount ?: current.totalCount
            )
        }
    }
    
    
    fun finish(taskKey: String, startedAt: LocalDateTime) {
        states.computeIfPresent(taskKey) { _, current ->
            if (current.startedAt == startedAt) current.copy(running = false) else current
        }
    }
    
    
    fun snapshot(): List<SyncTaskProgress> = states.values.sortedBy { it.taskKey }
}
