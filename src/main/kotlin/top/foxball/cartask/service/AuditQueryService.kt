package top.foxball.cartask.service

import top.foxball.cartask.entity.AuditEvent
import java.time.LocalDateTime
import java.util.*

interface AuditQueryService {
    data class Query(
        val occurredFrom: LocalDateTime? = null,
        val occurredTo: LocalDateTime? = null,
        val actorUserId: Long? = null,
        val action: String? = null,
        val targetType: String? = null,
        val targetId: String? = null,
        val result: AuditEvent.Result? = null,
        val riskLevel: AuditEvent.RiskLevel? = null,
        val requestId: String? = null,
        val page: Int = 1,
        val pageSize: Int = 20,
    )

    data class EventData(
        val eventId: UUID,
        val occurredAt: LocalDateTime,
        val recordedAt: LocalDateTime,
        val requestId: String?,
        val actorType: AuditEvent.ActorType,
        val actorUserId: Long?,
        val actorUsername: String,
        val actorRole: String?,
        val action: String,
        val category: AuditEvent.Category,
        val riskLevel: AuditEvent.RiskLevel,
        val targetType: String,
        val targetId: String?,
        val targetSummary: Any?,
        val scopeSummary: Any?,
        val result: AuditEvent.Result,
        val reasonCode: String?,
        val reason: String?,
        val beforeData: Any?,
        val afterData: Any?,
        val sourceSystem: String,
        val eventHash: String,
    )

    data class PageData(
        val events: List<EventData>,
        val page: Int,
        val pageSize: Int,
        val total: Long,
    )

    data class VerificationData(
        val partitionKey: String,
        val valid: Boolean,
        val eventCount: Int,
        val firstSequence: Long?,
        val lastSequence: Long?,
        val message: String,
    )

    /**
     * list：查询或读取相关数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param query 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun list(query: Query): PageData

    /**
     * get：查询或读取相关数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param eventId 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun get(eventId: UUID): EventData

    /**
     * export：执行数据同步、探测或文件处理。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param query 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun export(query: Query): List<EventData>

    /**
     * verify：校验输入、状态或访问条件。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param partitionKey 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun verify(partitionKey: String): VerificationData
}
