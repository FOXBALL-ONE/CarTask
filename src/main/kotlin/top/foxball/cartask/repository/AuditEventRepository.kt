package top.foxball.cartask.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import top.foxball.cartask.entity.AuditEvent
import java.time.LocalDateTime
import java.util.*

interface AuditEventRepository : JpaRepository<AuditEvent, Long>, JpaSpecificationExecutor<AuditEvent> {
    @Query("SELECT pg_advisory_xact_lock(hashtextextended(:partitionKey, 0))", nativeQuery = true)
            /**
             * lockPartition：执行当前模块中的业务操作。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @param partitionKey 参与本次处理的输入参数。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun lockPartition(@Param("partitionKey") partitionKey: String): Any?

    /**
     * findByEventId：查询或读取相关数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param eventId 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun findByEventId(eventId: UUID): AuditEvent?

    /**
     * findBySourceSystemAndActionAndIdempotencyKey：查询或读取相关数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param sourceSystem 参与本次处理的输入参数。
     * @param action 参与本次处理的输入参数。
     * @param idempotencyKey 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun findBySourceSystemAndActionAndIdempotencyKey(
        sourceSystem: String,
        action: String,
        idempotencyKey: String,
    ): AuditEvent?

    /**
     * findTopByPartitionKeyOrderBySequenceNoDesc：查询或读取相关数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param partitionKey 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun findTopByPartitionKeyOrderBySequenceNoDesc(partitionKey: String): AuditEvent?

    /**
     * findByPartitionKeyOrderBySequenceNoAsc：查询或读取相关数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param partitionKey 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun findByPartitionKeyOrderBySequenceNoAsc(partitionKey: String): List<AuditEvent>

    /**
     * findByOccurredAtBetweenOrderByOccurredAtDescEventIdDesc：查询或读取相关数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param occurredFrom 参与本次处理的输入参数。
     * @param occurredTo 参与本次处理的输入参数。
     * @param pageable 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun findByOccurredAtBetweenOrderByOccurredAtDescEventIdDesc(
        occurredFrom: LocalDateTime,
        occurredTo: LocalDateTime,
        pageable: Pageable,
    ): Page<AuditEvent>
}
