package top.foxball.cartask.service

import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicReference


enum class BackupPhase {
    IDLE,
    COUNTING,
    DUMPING,
    ARCHIVING,
    FINISHED,
    FAILED,
}


data class BackupProgress(
    val phase: BackupPhase,
    val label: String,
    val tablesDone: Int,
    val tablesTotal: Int,
    val rowsDone: Long,
    val rowsTotal: Long,
    val filesDone: Int,
    val filesTotal: Int,
    val startedAt: LocalDateTime?,
    val finishedAt: LocalDateTime?,
    val message: String?,
) {
    
    val percent: Int
        get() = when (phase) {
            BackupPhase.IDLE -> 0
            BackupPhase.COUNTING -> ratio(tablesDone.toLong(), tablesTotal.toLong(), COUNTING_CEILING)
            BackupPhase.DUMPING -> {
                val span = DUMPING_CEILING - COUNTING_CEILING
                val done = if (rowsTotal > 0) ratio(rowsDone, rowsTotal, span)
                else ratio(tablesDone.toLong(), tablesTotal.toLong(), span)
                COUNTING_CEILING + done
            }
            
            BackupPhase.ARCHIVING -> if (filesTotal == 0) 100
            else DUMPING_CEILING + ratio(filesDone.toLong(), filesTotal.toLong(), 100 - DUMPING_CEILING)
            
            BackupPhase.FINISHED -> 100
            BackupPhase.FAILED -> 0
        }
    
    
    private fun ratio(done: Long, total: Long, span: Int): Int =
        if (total <= 0) 0 else (done * span / total).toInt().coerceIn(0, span)
    
    companion object {
        
        private const val COUNTING_CEILING = 5
        private const val DUMPING_CEILING = 80
        
        
        fun idle(): BackupProgress = BackupProgress(
            phase = BackupPhase.IDLE,
            label = "空闲",
            tablesDone = 0,
            tablesTotal = 0,
            rowsDone = 0,
            rowsTotal = 0,
            filesDone = 0,
            filesTotal = 0,
            startedAt = null,
            finishedAt = null,
            message = null,
        )
    }
}


@Service
class BackupProgressService {
    private val current = AtomicReference(BackupProgress.idle())
    
    
    fun snapshot(): BackupProgress = current.get()
    
    
    fun startCounting(startedAt: LocalDateTime = LocalDateTime.now()) {
        current.set(
            BackupProgress.idle().copy(
                phase = BackupPhase.COUNTING,
                label = "统计备份范围",
                startedAt = startedAt,
            )
        )
    }
    
    
    fun counting(tablesDone: Int, tablesTotal: Int) {
        current.updateAndGet { it.copy(tablesDone = tablesDone, tablesTotal = tablesTotal) }
    }
    
    
    fun startDumping(tablesTotal: Int, rowsTotal: Long) {
        current.updateAndGet {
            it.copy(
                phase = BackupPhase.DUMPING,
                label = "导出表结构与数据",
                tablesDone = 0,
                tablesTotal = tablesTotal,
                rowsDone = 0,
                rowsTotal = rowsTotal,
            )
        }
    }
    
    
    fun dumping(tablesDone: Int, rowsDone: Long) {
        current.updateAndGet { it.copy(tablesDone = tablesDone, rowsDone = rowsDone) }
    }
    
    
    fun startArchiving(filesTotal: Int) {
        current.updateAndGet {
            it.copy(phase = BackupPhase.ARCHIVING, label = "打包附件", filesDone = 0, filesTotal = filesTotal)
        }
    }
    
    
    fun archiving(filesDone: Int) {
        current.updateAndGet { it.copy(filesDone = filesDone) }
    }
    
    
    fun finish() {
        current.updateAndGet {
            it.copy(phase = BackupPhase.FINISHED, label = "备份完成", finishedAt = LocalDateTime.now())
        }
    }
    
    
    fun fail(reason: String) {
        current.updateAndGet {
            it.copy(
                phase = BackupPhase.FAILED,
                label = "备份失败",
                finishedAt = LocalDateTime.now(),
                message = reason.take(300),
            )
        }
    }
    
    private fun AtomicReference<BackupProgress>.updateAndGet(block: (BackupProgress) -> BackupProgress) {
        while (true) {
            val existing = get()
            val updated = block(existing)
            if (compareAndSet(existing, updated)) return
        }
    }
}
