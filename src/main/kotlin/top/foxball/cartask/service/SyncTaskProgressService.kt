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

    /**
     * start：执行当前模块中的业务操作。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param taskKey 参与本次处理的输入参数。
     * @param taskName 参与本次处理的输入参数。
     * @param startedAt 参与本次处理的输入参数。
     * @param totalCount 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun start(taskKey: String, taskName: String, startedAt: LocalDateTime, totalCount: Int? = null) {
        states.compute(taskKey) { _, current ->
            current?.takeIf { it.running }
                ?: SyncTaskProgress(taskKey, taskName, true, 0, totalCount, startedAt)
        }
    }

    /**
     * update：更新业务状态或修改相关配置。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param taskKey 参与本次处理的输入参数。
     * @param processedCount 参与本次处理的输入参数。
     * @param totalCount 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun update(taskKey: String, processedCount: Int, totalCount: Int? = null) {
        states.computeIfPresent(taskKey) { _, current ->
            current.copy(
                processedCount = processedCount,
                totalCount = totalCount ?: current.totalCount
            )
        }
    }

    /**
     * finish：执行当前模块中的业务操作。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param taskKey 参与本次处理的输入参数。
     * @param startedAt 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun finish(taskKey: String, startedAt: LocalDateTime) {
        states.computeIfPresent(taskKey) { _, current ->
            if (current.startedAt == startedAt) current.copy(running = false) else current
        }
    }

    /**
     * snapshot：执行当前模块中的业务操作。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun snapshot(): List<SyncTaskProgress> = states.values.sortedBy { it.taskKey }
}
