package top.foxball.cartask.service.impl

import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.beans.BeanWrapperImpl
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import top.foxball.cartask.audit.AuditAction
import top.foxball.cartask.audit.AuditCommand
import top.foxball.cartask.audit.AuditService
import top.foxball.cartask.authentication.CurrentUserPrincipal
import top.foxball.cartask.entity.AccessControl
import top.foxball.cartask.repository.AccessControlRepository
import top.foxball.cartask.service.AccessControlService

@Service
/** 基于 JPA 的门禁授权记录服务。 */
class AccessControlServiceImpl(
    private val repository: AccessControlRepository,
    private val auditService: AuditService? = null,
) : AccessControlService {
    @Transactional
    /**
     * create：创建、保存或初始化相关数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param entity 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    override fun create(entity: AccessControl): AccessControl {
        require(entityId(entity) == null) { "创建记录时不能指定 ID" }
        requireValidAuthorizationPeriod(entity)
        entity.reviewStatus = AccessControl.ReviewStatus.PENDING
        entity.synchronizedLoading = false
        val saved = repository.save(entity)
        auditService?.record(
            AuditCommand(
                AuditAction.ACCESS_CONTROL_CREATED,
                "access_control",
                saved.id?.toString(),
                targetSummary = mapOf("name" to saved.name, "department_id" to saved.department?.id),
                afterData = mapOf(
                    "review_status" to saved.reviewStatus.name,
                    "synchronized" to saved.synchronizedLoading
                ),
            ),
        )
        return saved
    }

    @Transactional
    /**
     * createBatch：创建、保存或初始化相关数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param entities 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    override fun createBatch(entities: List<AccessControl>): List<AccessControl> {
        require(entities.isNotEmpty()) { "创建列表不能为空" }
        require(entities.all { entityId(it) == null }) { "创建记录时不能指定 ID" }
        entities.forEach {
            requireValidAuthorizationPeriod(it)
            it.reviewStatus = AccessControl.ReviewStatus.PENDING
            it.synchronizedLoading = false
        }
        val saved = repository.saveAll(entities)
        saved.forEach {
            auditService?.record(
                AuditCommand(
                    AuditAction.ACCESS_CONTROL_CREATED,
                    "access_control",
                    it.id?.toString(),
                    targetSummary = mapOf("name" to it.name, "department_id" to it.department?.id),
                    afterData = mapOf(
                        "review_status" to it.reviewStatus.name,
                        "synchronized" to it.synchronizedLoading
                    ),
                ),
            )
        }
        return saved
    }

    @Transactional
    /**
     * get：查询或读取相关数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param id 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    override fun get(id: Long): AccessControl = repository.findById(id)
        .orElseThrow { IllegalArgumentException("记录不存在: $id") }

    @Transactional
    /**
     * getBatch：查询或读取相关数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param ids 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    override fun getBatch(ids: List<Long>): List<AccessControl> {
        require(ids.isNotEmpty()) { "ID 列表不能为空" }
        require(ids.all { it > 0 }) { "ID 必须大于 0" }
        val distinctIds = ids.distinct()
        val recordsById = repository.findAllById(distinctIds).associateBy { entityId(it) }
        val missingIds = distinctIds.filterNot(recordsById::containsKey)
        require(missingIds.isEmpty()) { "部分记录不存在: ${missingIds.joinToString(",")}" }
        return ids.map { recordsById.getValue(it) }
    }

    @Transactional
    /**
     * list：查询或读取相关数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param page 参与本次处理的输入参数。
     * @param pageSize 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    override fun list(page: Int, pageSize: Int): Page<AccessControl> {
        require(page >= 1) { "页码必须大于 0" }
        require(pageSize in 1..100) { "每页数量必须在 1 到 100 之间" }
        return repository.findAll(PageRequest.of(page - 1, pageSize))
    }

    @Transactional
    /**
     * update：更新业务状态或修改相关配置。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param id 参与本次处理的输入参数。
     * @param entity 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    override fun update(id: Long, entity: AccessControl): AccessControl {
        require(id > 0) { "ID 必须大于 0" }
        require(entityId(entity) == id) { "路径 ID 必须与请求体 ID 一致" }
        val current = repository.findById(id)
            .orElseThrow { IllegalArgumentException("记录不存在: $id") }
        if (current.reviewStatus == AccessControl.ReviewStatus.REJECTED) {
            throw AccessDeniedException("已驳回的门禁申请必须重新提交")
        }
        val before = mapOf("review_status" to current.reviewStatus.name, "synchronized" to current.synchronizedLoading)
        copyEditableProperties(entity, current)
        requireValidAuthorizationPeriod(current)
        if (current.reviewStatus == AccessControl.ReviewStatus.APPROVED) {
            current.reviewStatus = AccessControl.ReviewStatus.PENDING
            current.synchronizedLoading = false
        }
        val saved = repository.save(current)
        auditService?.record(
            AuditCommand(
                AuditAction.ACCESS_CONTROL_UPDATED,
                "access_control",
                id.toString(),
                beforeData = before,
                afterData = mapOf(
                    "review_status" to saved.reviewStatus.name,
                    "synchronized" to saved.synchronizedLoading
                ),
            ),
        )
        return saved
    }

    @Transactional
    /**
     * updateBatch：更新业务状态或修改相关配置。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param entities 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    override fun updateBatch(entities: List<AccessControl>): List<AccessControl> {
        require(entities.isNotEmpty()) { "更新列表不能为空" }
        val ids = entities.map { entityId(it) }
        require(ids.all { it != null && it > 0 }) { "更新记录必须提供有效 ID" }
        require(ids.distinct().size == ids.size) { "更新记录的 ID 不能重复" }
        val currentById = repository.findAllById(ids.filterNotNull()).associateBy { entityId(it) }
        val missingIds = ids.filterNotNull().filterNot(currentById::containsKey)
        require(missingIds.isEmpty()) { "部分记录不存在: ${missingIds.joinToString(",")}" }
        val beforeById = currentById.mapValues { (_, accessControl) ->
            mapOf(
                "review_status" to accessControl.reviewStatus.name,
                "synchronized" to accessControl.synchronizedLoading
            )
        }
        val updated = entities.map { incoming ->
            val current = currentById.getValue(entityId(incoming))
            if (current.reviewStatus == AccessControl.ReviewStatus.REJECTED) {
                throw AccessDeniedException("已驳回的门禁申请必须重新提交")
            }
            copyEditableProperties(incoming, current)
            requireValidAuthorizationPeriod(current)
            if (current.reviewStatus == AccessControl.ReviewStatus.APPROVED) {
                current.reviewStatus = AccessControl.ReviewStatus.PENDING
                current.synchronizedLoading = false
            }
            current
        }
        val saved = repository.saveAll(updated)
        saved.forEach { accessControl ->
            auditService?.record(
                AuditCommand(
                    AuditAction.ACCESS_CONTROL_UPDATED,
                    "access_control",
                    accessControl.id?.toString(),
                    beforeData = beforeById[accessControl.id],
                    afterData = mapOf(
                        "review_status" to accessControl.reviewStatus.name,
                        "synchronized" to accessControl.synchronizedLoading
                    ),
                ),
            )
        }
        return saved
    }

    @Transactional
    /**
     * review：执行当前模块中的业务操作。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param id 参与本次处理的输入参数。
     * @param approved 参与本次处理的输入参数。
     * @param reason 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    override fun review(id: Long, approved: Boolean, reason: String): AccessControl {
        require(reason.isNotBlank()) { "审核原因不能为空" }
        val current = repository.findById(id)
            .orElseThrow { IllegalArgumentException("记录不存在: $id") }
        if (current.reviewStatus != AccessControl.ReviewStatus.PENDING || current.synchronizedLoading) {
            throw AccessDeniedException("当前门禁申请状态不允许审核")
        }
        val actor = actorName()
        val before = current.reviewStatus.name
        current.reviewStatus =
            if (approved) AccessControl.ReviewStatus.APPROVED else AccessControl.ReviewStatus.REJECTED
        logger.warn(
            "门禁授权审核，accessControlId={}, actor={}, approved={}, reason={}",
            id,
            actor,
            approved,
            reason,
        )
        val saved = repository.save(current)
        auditService?.record(
            AuditCommand(
                AuditAction.ACCESS_CONTROL_REVIEWED,
                "access_control",
                id.toString(),
                reason = reason,
                beforeData = mapOf("review_status" to before),
                afterData = mapOf("review_status" to saved.reviewStatus.name),
            ),
        )
        return saved
    }

    @Transactional
    /**
     * synchronize：执行数据同步、探测或文件处理。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param id 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    override fun synchronize(id: Long): AccessControl {
        val current = repository.findById(id)
            .orElseThrow { IllegalArgumentException("记录不存在: $id") }
        if (current.reviewStatus != AccessControl.ReviewStatus.APPROVED || current.synchronizedLoading) {
            throw AccessDeniedException("只有未同步的已审核授权可以下发")
        }
        throw IllegalStateException("门禁设备同步尚未接入，不能标记为已同步")
    }

    @Transactional
    /**
     * delete：删除、清理或撤销相关数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param id 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    override fun delete(id: Long) {
        throw AccessDeniedException("门禁授权不允许物理删除")
    }

    @Transactional
    /**
     * deleteBatch：删除、清理或撤销相关数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param ids 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    override fun deleteBatch(ids: List<Long>) {
        throw AccessDeniedException("门禁授权不允许物理删除")
    }

    /**
     * entityId：执行当前模块中的业务操作。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param entity 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    private fun entityId(entity: AccessControl): Long? {
        var type: Class<*>? = entity.javaClass
        while (type != null) {
            try {
                val field = type.getDeclaredField("id")
                field.isAccessible = true
                return (field.get(entity) as? Number)?.toLong()
            } catch (_: NoSuchFieldException) {
                type = type.superclass
            }
        }
        throw IllegalArgumentException("实体缺少 Long 类型的 id 属性")
    }

    /**
     * copyEditableProperties：执行当前模块中的业务操作。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param source 参与本次处理的输入参数。
     * @param target 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    private fun copyEditableProperties(source: AccessControl, target: AccessControl) {
        val sourceWrapper = BeanWrapperImpl(source)
        val targetWrapper = BeanWrapperImpl(target)
        val protectedProperties = setOf(
            "id", "createdAt", "updatedAt", "updateTime", "passwordHash", "reviewStatus", "synchronizedLoading",
        )
        targetWrapper.propertyDescriptors
            .asSequence()
            .map { it.name }
            .filterNot(protectedProperties::contains)
            .filter { sourceWrapper.isReadableProperty(it) && targetWrapper.isWritableProperty(it) }
            .forEach { property ->
                targetWrapper.setPropertyValue(property, sourceWrapper.getPropertyValue(property))
            }
    }

    /**
     * actorName：执行当前模块中的业务操作。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    private fun actorName(): String =
        (SecurityContextHolder.getContext().authentication?.principal as? CurrentUserPrincipal)?.username
            ?: throw AccessDeniedException("缺少有效的审核人上下文")

    /**
     * requireValidAuthorizationPeriod：校验输入、状态或访问条件。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param entity 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    private fun requireValidAuthorizationPeriod(entity: AccessControl) {
        val start = entity.upTime
        val end = entity.endTime
        require(end == null || (start != null && end.isAfter(start))) {
            "授权结束时间必须晚于开始时间"
        }
    }

    private companion object {
        val logger = LoggerFactory.getLogger(AccessControlServiceImpl::class.java)
    }
}
