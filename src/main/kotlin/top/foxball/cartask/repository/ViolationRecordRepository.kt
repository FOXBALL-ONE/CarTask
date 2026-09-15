package top.foxball.cartask.repository

import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import top.foxball.cartask.entity.ViolationRecord

interface ViolationRecordRepository : JpaRepository<ViolationRecord, Long> {
    @EntityGraph(attributePaths = ["subject", "violationType"])
    @Query("select record from ViolationRecord record join fetch record.violationType join fetch record.subject")
            /**
             * findAllWithViolationType：查询或读取相关数据。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun findAllWithViolationType(): List<ViolationRecord>

    /**
     * existsByViolationTypeId：查询或读取相关数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param violationTypeId 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun existsByViolationTypeId(violationTypeId: Long): Boolean
}
