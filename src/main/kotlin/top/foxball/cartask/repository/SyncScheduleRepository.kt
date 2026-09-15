package top.foxball.cartask.repository

import org.springframework.data.jpa.repository.JpaRepository
import top.foxball.cartask.entity.SyncSchedule

/** 同步计划数据访问接口，提供计划配置的持久化与查询能力。 */
interface SyncScheduleRepository : JpaRepository<SyncSchedule, String>
