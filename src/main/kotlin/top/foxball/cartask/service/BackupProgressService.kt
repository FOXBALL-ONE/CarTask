package top.foxball.cartask.service

import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicReference

/** 备份当前处在哪个阶段。 */
enum class BackupPhase {
    IDLE,
    COUNTING,
    DUMPING,
    ARCHIVING,
    FINISHED,
    FAILED,
}

/**
 * 一次备份的进度快照。
 *
 * 三种计数分开给：表是结构导出与逐表读取的单位，行是数据量的单位，附件是打包阶段的单位。
 * 只给一个笼统的百分比，出问题时看不出是卡在某张大表上还是卡在某个大附件上。
 */
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
    /** 0-100 的总进度；统计范围阶段还不知道总量，按已统计的表数推进。 */
    val percent: Int
        get() = when (phase) {
            BackupPhase.IDLE -> 0
            BackupPhase.COUNTING -> ratio(tablesDone.toLong(), tablesTotal.toLong(), COUNTING_CEILING)
            BackupPhase.DUMPING -> {
                val span = DUMPING_CEILING - COUNTING_CEILING
                // 统计不到行数（例如全是空表）时退回按表数推进，免得整段卡在 5%。
                val done = if (rowsTotal > 0) ratio(rowsDone, rowsTotal, span)
                else ratio(tablesDone.toLong(), tablesTotal.toLong(), span)
                COUNTING_CEILING + done
            }
            // 没有附件时打包只剩写清单，没有可推进的量，直接算到位而不是停在 80。
            BackupPhase.ARCHIVING -> if (filesTotal == 0) 100
            else DUMPING_CEILING + ratio(filesDone.toLong(), filesTotal.toLong(), 100 - DUMPING_CEILING)

            BackupPhase.FINISHED -> 100
            BackupPhase.FAILED -> 0
        }

    /**
     * ratio：执行当前模块中的业务操作。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param done 参与本次处理的输入参数。
     * @param total 参与本次处理的输入参数。
     * @param span 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    private fun ratio(done: Long, total: Long, span: Int): Int =
        if (total <= 0) 0 else (done * span / total).toInt().coerceIn(0, span)

    companion object {
        /** 统计阶段占前 5%，导出到 80%，剩下 20% 留给附件打包。 */
        private const val COUNTING_CEILING = 5
        private const val DUMPING_CEILING = 80

        /**
         * idle：执行当前模块中的业务操作。
         *
         * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
         * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
         * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
         */
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

/**
 * 备份进度的读写。
 *
 * 同一时刻只允许一次备份（见 [top.foxball.cartask.service.impl.DataBackupServiceImpl] 的导出锁），
 * 所以只保留一份最新快照即可，不需要按任务键分组。
 *
 * 进度刻意放在内存里、不落库：它描述的是"此刻"，进程重启后一次没跑完的备份本来也不会继续，
 * 落一条永远不会更新的记录反而会让人以为还有备份在跑。
 */
@Service
class BackupProgressService {
    private val current = AtomicReference(BackupProgress.idle())

    /**
     * snapshot：执行当前模块中的业务操作。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun snapshot(): BackupProgress = current.get()

    /**
     * startCounting：执行当前模块中的业务操作。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param startedAt 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun startCounting(startedAt: LocalDateTime = LocalDateTime.now()) {
        current.set(
            BackupProgress.idle().copy(
                phase = BackupPhase.COUNTING,
                label = "统计备份范围",
                startedAt = startedAt,
            )
        )
    }

    /** 统计阶段每数完一张表报一次，让前端看到它在往前走。 */
    fun counting(tablesDone: Int, tablesTotal: Int) {
        current.updateAndGet { it.copy(tablesDone = tablesDone, tablesTotal = tablesTotal) }
    }

    /**
     * startDumping：执行当前模块中的业务操作。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param tablesTotal 参与本次处理的输入参数。
     * @param rowsTotal 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
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

    /**
     * dumping：执行当前模块中的业务操作。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param tablesDone 参与本次处理的输入参数。
     * @param rowsDone 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun dumping(tablesDone: Int, rowsDone: Long) {
        current.updateAndGet { it.copy(tablesDone = tablesDone, rowsDone = rowsDone) }
    }

    /**
     * startArchiving：执行当前模块中的业务操作。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param filesTotal 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun startArchiving(filesTotal: Int) {
        current.updateAndGet {
            it.copy(phase = BackupPhase.ARCHIVING, label = "打包附件", filesDone = 0, filesTotal = filesTotal)
        }
    }

    /**
     * archiving：执行当前模块中的业务操作。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param filesDone 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun archiving(filesDone: Int) {
        current.updateAndGet { it.copy(filesDone = filesDone) }
    }

    /**
     * finish：执行当前模块中的业务操作。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun finish() {
        current.updateAndGet {
            it.copy(phase = BackupPhase.FINISHED, label = "备份完成", finishedAt = LocalDateTime.now())
        }
    }

    /**
     * fail：执行当前模块中的业务操作。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param reason 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun fail(reason: String) {
        current.updateAndGet {
            it.copy(
                phase = BackupPhase.FAILED,
                label = "备份失败",
                finishedAt = LocalDateTime.now(),
                // 失败原因里可能带库名、路径这类信息，只留前 300 字，够定位就行。
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
