package top.foxball.cartask.repository

import org.springframework.data.jpa.repository.JpaRepository
import top.foxball.cartask.entity.OperationLog

/** 操作日志数据访问接口，封装日志记录的持久化查询能力。 */
interface OperationLogRepository : JpaRepository<OperationLog, Long>
