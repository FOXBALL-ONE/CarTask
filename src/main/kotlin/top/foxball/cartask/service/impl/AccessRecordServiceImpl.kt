package top.foxball.cartask.service.impl

import jakarta.transaction.Transactional
import org.springframework.beans.BeanWrapperImpl
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.SecurityContextHolder
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import top.foxball.cartask.entity.AccessRecord
import top.foxball.cartask.repository.AccessRecordRepository
import top.foxball.cartask.service.AccessRecordService

@Service
/** 基于 JPA 的车辆进出记录服务。 */
class AccessRecordServiceImpl(
    private val repository: AccessRecordRepository,
) : AccessRecordService {
    @Transactional
    override fun create(entity: AccessRecord): AccessRecord {
        throw AccessDeniedException("进出流水只能由设备同步任务写入")
    }

    @Transactional
    override fun createBatch(entities: List<AccessRecord>): List<AccessRecord> {
        throw AccessDeniedException("进出流水只能由设备同步任务写入")
    }

    @Transactional
    override fun get(id: Long): AccessRecord = repository.findById(id)
        .orElseThrow { IllegalArgumentException("记录不存在: $id") }

    @Transactional
    override fun getBatch(ids: List<Long>): List<AccessRecord> {
        require(ids.isNotEmpty()) { "ID 列表不能为空" }
        require(ids.all { it > 0 }) { "ID 必须大于 0" }
        val distinctIds = ids.distinct()
        val recordsById = repository.findAllById(distinctIds).associateBy { entityId(it) }
        val missingIds = distinctIds.filterNot(recordsById::containsKey)
        require(missingIds.isEmpty()) { "部分记录不存在: ${missingIds.joinToString(",")}" }
        return ids.map { recordsById.getValue(it) }
    }

    @Transactional
    override fun list(page: Int, pageSize: Int): Page<AccessRecord> {
        require(page >= 1) { "页码必须大于 0" }
        require(pageSize in 1..100) { "每页数量必须在 1 到 100 之间" }
        return repository.findAll(PageRequest.of(page - 1, pageSize))
    }

    @Transactional
    override fun update(id: Long, entity: AccessRecord): AccessRecord {
        throw AccessDeniedException("进出流水只能通过带原因的更正接口修改")
    }

    @Transactional
    override fun updateBatch(entities: List<AccessRecord>): List<AccessRecord> {
        throw AccessDeniedException("进出流水只能通过带原因的更正接口修改")
    }

    @Transactional
    override fun delete(id: Long) {
        throw AccessDeniedException("进出流水不允许物理删除")
    }

    @Transactional
    override fun deleteBatch(ids: List<Long>) {
        throw AccessDeniedException("进出流水不允许物理删除")
    }

    @Transactional
    override fun correct(id: Long, entity: AccessRecord, reason: String): AccessRecord {
        require(reason.isNotBlank()) { "更正原因不能为空" }
        require(reason.trim().length <= 512) { "更正原因不能超过 512 个字符" }
        require(id > 0) { "ID 必须大于 0" }
        require(entityId(entity) == id) { "路径 ID 必须与请求体 ID 一致" }
        val current = repository.findById(id)
            .orElseThrow { IllegalArgumentException("记录不存在: $id") }
        val before = "${current.carNumber}|${current.inAndOut}|${current.inAndOutTime}"
        copyEditableProperties(entity, current)
        logger.warn("进出流水更正，recordId={}, actor={}, reason={}, before={}, after={}", id, actorName(), reason, before, "${current.carNumber}|${current.inAndOut}|${current.inAndOutTime}")
        return repository.save(current)
    }

    @Transactional
    override fun correctBatch(entities: List<AccessRecord>, reason: String): List<AccessRecord> {
        require(entities.isNotEmpty()) { "更正列表不能为空" }
        require(reason.isNotBlank()) { "更正原因不能为空" }
        val ids = entities.map { entityId(it) }
        require(ids.all { it != null && it > 0 }) { "更正记录必须提供有效 ID" }
        require(ids.distinct().size == ids.size) { "更正记录的 ID 不能重复" }
        return entities.map { correct(requireNotNull(entityId(it)), it, reason) }
    }

    @Transactional
    override fun release(id: Long, reason: String): AccessRecord {
        require(reason.isNotBlank()) { "放行原因不能为空" }
        require(reason.trim().length <= 512) { "放行原因不能超过 512 个字符" }
        val current = repository.findById(id)
            .orElseThrow { IllegalArgumentException("记录不存在: $id") }
        require(!current.carNumber.isNullOrBlank()) { "人工放行必须关联车辆" }
        require(current.releaseChannel == null) { "已有放行渠道的流水不能再次人工放行" }
        val actor = actorName()
        current.releaseChannel = AccessRecord.ReleaseChannel.MANUAL
        current.releaseInstructions = reason.trim()
        current.operatorName = actor
        logger.warn("人工放行，recordId={}, actor={}, reason={}", id, actor, reason)
        return repository.save(current)
    }

    private fun actorName(): String =
        (SecurityContextHolder.getContext().authentication?.principal as? top.foxball.cartask.authentication.CurrentUserPrincipal)?.username
            ?: throw AccessDeniedException("缺少有效的操作人上下文")

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
    }
}
