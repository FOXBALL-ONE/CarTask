package top.foxball.cartask.service.impl

import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.beans.BeanWrapperImpl
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import top.foxball.cartask.audit.AuditAction
import top.foxball.cartask.audit.AuditCommand
import top.foxball.cartask.audit.AuditService
import top.foxball.cartask.entity.AccessRecord
import top.foxball.cartask.repository.AccessRecordRepository
import top.foxball.cartask.service.AccessRecordService
import java.time.format.DateTimeFormatter

@Service
/** 基于 JPA 的车辆进出记录服务。 */
class AccessRecordServiceImpl(
    private val repository: AccessRecordRepository,
    private val auditService: AuditService? = null,
) : AccessRecordService {
    @Transactional
    /**
     * create：创建、保存或初始化相关数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param entity 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    override fun create(entity: AccessRecord): AccessRecordService.AccessRecordData {
        throw AccessDeniedException("进出流水只能由设备同步任务写入")
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
    override fun createBatch(entities: List<AccessRecord>): List<AccessRecordService.AccessRecordData> {
        throw AccessDeniedException("进出流水只能由设备同步任务写入")
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
    override fun get(id: Long): AccessRecordService.AccessRecordData = repository.findById(id)
        .orElseThrow { IllegalArgumentException("记录不存在: $id") }
        .let(::toData)

    @Transactional
    /**
     * getBatch：查询或读取相关数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param ids 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    override fun getBatch(ids: List<Long>): List<AccessRecordService.AccessRecordData> {
        require(ids.isNotEmpty()) { "ID 列表不能为空" }
        require(ids.all { it > 0 }) { "ID 必须大于 0" }
        val distinctIds = ids.distinct()
        val recordsById = repository.findAllById(distinctIds).associateBy { entityId(it) }
        val missingIds = distinctIds.filterNot(recordsById::containsKey)
        require(missingIds.isEmpty()) { "部分记录不存在: ${missingIds.joinToString(",")}" }
        return ids.map { toData(recordsById.getValue(it)) }
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
    override fun list(page: Int, pageSize: Int): AccessRecordService.PageData {
        require(page >= 1) { "页码必须大于 0" }
        require(pageSize in 1..100) { "每页数量必须在 1 到 100 之间" }
        val result = repository.findAll(org.springframework.data.domain.PageRequest.of(page - 1, pageSize))
        return AccessRecordService.PageData(
            records = result.content.map(::toData),
            page = page,
            pageSize = pageSize,
            total = result.totalElements,
        )
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
    override fun update(id: Long, entity: AccessRecord): AccessRecordService.AccessRecordData {
        throw AccessDeniedException("进出流水只能通过带原因的更正接口修改")
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
    override fun updateBatch(entities: List<AccessRecord>): List<AccessRecordService.AccessRecordData> {
        throw AccessDeniedException("进出流水只能通过带原因的更正接口修改")
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
        throw AccessDeniedException("进出流水不允许物理删除")
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
        throw AccessDeniedException("进出流水不允许物理删除")
    }

    @Transactional
    /**
     * correct：执行当前模块中的业务操作。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param id 参与本次处理的输入参数。
     * @param entity 参与本次处理的输入参数。
     * @param reason 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    override fun correct(
        id: Long,
        entity: AccessRecord,
        reason: String,
    ): AccessRecordService.AccessRecordData {
        require(reason.isNotBlank()) { "更正原因不能为空" }
        require(reason.trim().length <= 512) { "更正原因不能超过 512 个字符" }
        require(id > 0) { "ID 必须大于 0" }
        require(entityId(entity) == id) { "路径 ID 必须与请求体 ID 一致" }
        val current = repository.findById(id)
            .orElseThrow { IllegalArgumentException("记录不存在: $id") }
        val beforeData = mapOf(
            "car_number" to maskCar(current.carNumber),
            "in_and_out" to current.inAndOut.name,
            "in_and_out_time" to current.inAndOutTime.toString(),
        )
        copyEditableProperties(entity, current)
        logger.warn(
            "进出流水更正，recordId={}, actor={}, reason={}, before={}, after={}",
            id,
            actorName(),
            reason,
            beforeData,
            "${current.carNumber}|${current.inAndOut}|${current.inAndOutTime}",
        )
        val saved = repository.save(current)
        auditService?.record(
            AuditCommand(
                AuditAction.ACCESS_RECORD_CORRECTED,
                "access_record",
                id.toString(),
                reason = reason,
                beforeData = beforeData,
                afterData = mapOf("car_number" to maskCar(saved.carNumber), "in_and_out" to saved.inAndOut.name),
            ),
        )
        return toData(saved)
    }

    @Transactional
    /**
     * correctBatch：执行当前模块中的业务操作。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param entities 参与本次处理的输入参数。
     * @param reason 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    override fun correctBatch(
        entities: List<AccessRecord>,
        reason: String,
    ): List<AccessRecordService.AccessRecordData> {
        require(entities.isNotEmpty()) { "更正列表不能为空" }
        require(reason.isNotBlank()) { "更正原因不能为空" }
        val ids = entities.map { entityId(it) }
        require(ids.all { it != null && it > 0 }) { "更正记录必须提供有效 ID" }
        require(ids.distinct().size == ids.size) { "更正记录的 ID 不能重复" }
        return entities.map { correct(requireNotNull(entityId(it)), it, reason) }
    }

    @Transactional
    /**
     * release：执行当前模块中的业务操作。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param id 参与本次处理的输入参数。
     * @param reason 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    override fun release(id: Long, reason: String): AccessRecordService.AccessRecordData {
        require(reason.isNotBlank()) { "放行原因不能为空" }
        require(reason.trim().length <= 512) { "放行原因不能超过 512 个字符" }
        val current = repository.findById(id)
            .orElseThrow { IllegalArgumentException("记录不存在: $id") }
        require(!current.carNumber.isNullOrBlank()) { "人工放行必须关联车辆" }
        require(current.releaseChannel == null) { "已有放行渠道的流水不能再次人工放行" }
        val actor = actorName()
        current.releaseChannel = AccessRecord.ReleaseChannel.MANUAL
        current.passType = "人工放行"
        current.releaseInstructions = reason.trim()
        current.operatorName = actor
        logger.warn("人工放行，recordId={}, actor={}, reason={}", id, actor, reason)
        val saved = repository.save(current)
        auditService?.record(
            AuditCommand(
                AuditAction.ACCESS_RECORD_RELEASED,
                "access_record",
                id.toString(),
                reason = reason,
                afterData = mapOf(
                    "release_channel" to saved.releaseChannel?.name,
                    "operator_name" to saved.operatorName
                ),
            ),
        )
        return toData(saved)
    }

    /**
     * toData：转换、构建或格式化数据。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param record 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    private fun toData(record: AccessRecord): AccessRecordService.AccessRecordData =
        AccessRecordService.AccessRecordData(
            id = requireNotNull(record.id),
            plate = record.carNumber,
            owner = record.carOwnerName,
            dept = record.departmentName,
            time = record.inAndOutTime.format(DISPLAY_TIME),
            direction = if (record.inAndOut == AccessRecord.InAndOut.IN) "进" else "出",
            gate = record.gateName,
            vehicleType = AccessRecord.displayVehicleTypeName(record.vehicleTypeName) ?: record.carType?.carName,
            passType = record.passType ?: when (record.releaseChannel) {
                AccessRecord.ReleaseChannel.AUTOMATIC -> "自动放行"
                AccessRecord.ReleaseChannel.MANUAL -> "人工放行"
                AccessRecord.ReleaseChannel.REMOTE -> "远程放行"
                AccessRecord.ReleaseChannel.UNKNOWN -> "未知"
                null -> null
            },
            passDesc = record.releaseInstructions,
            photo = record.photoUrl,
        )

    /**
     * actorName：执行当前模块中的业务操作。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    private fun actorName(): String =
        (SecurityContextHolder.getContext().authentication?.principal as? top.foxball.cartask.authentication.CurrentUserPrincipal)?.username
            ?: throw AccessDeniedException("缺少有效的操作人上下文")

    /**
     * maskCar：执行当前模块中的业务操作。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param value 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    private fun maskCar(value: String?): String? = value?.let { if (it.length <= 4) it else "***${it.takeLast(4)}" }

    /**
     * entityId：执行当前模块中的业务操作。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param entity 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    private fun entityId(entity: AccessRecord): Long? {
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
    private fun copyEditableProperties(source: AccessRecord, target: AccessRecord) {
        val sourceWrapper = BeanWrapperImpl(source)
        val targetWrapper = BeanWrapperImpl(target)
        val protectedProperties = setOf(
            "id", "createdAt", "updatedAt", "updateTime", "passwordHash",
            "releaseInstructions", "releaseChannel", "operatorName",
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

    private companion object {
        val logger = LoggerFactory.getLogger(AccessRecordServiceImpl::class.java)
        val DISPLAY_TIME: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    }
}
