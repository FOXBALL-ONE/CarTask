package top.foxball.cartask.service.impl

import jakarta.transaction.Transactional
import org.springframework.beans.BeanWrapperImpl
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import top.foxball.cartask.entity.CarMasterInfo
import top.foxball.cartask.repository.CarMasterInfoRepository
import top.foxball.cartask.service.CarMasterInfoService

@Service
/** 基于 JPA 的车辆主档服务。 */
class CarMasterInfoServiceImpl(
    private val carMasterInfoRepository: CarMasterInfoRepository,
) : CarMasterInfoService {
    @Transactional
    override fun create(entity: CarMasterInfo): CarMasterInfo {
        require(entityId(entity) == null) { "创建记录时不能指定 ID" }
        return carMasterInfoRepository.save(entity)
    }

    @Transactional
    override fun createBatch(entities: List<CarMasterInfo>): List<CarMasterInfo> {
        require(entities.isNotEmpty()) { "创建列表不能为空" }
        require(entities.all { entityId(it) == null }) { "创建记录时不能指定 ID" }
        return carMasterInfoRepository.saveAll(entities)
    }

    @Transactional
    override fun get(id: Long): CarMasterInfo = carMasterInfoRepository.findById(id)
        .orElseThrow { IllegalArgumentException("记录不存在: $id") }

    @Transactional
    override fun getBatch(ids: List<Long>): List<CarMasterInfo> {
        require(ids.isNotEmpty()) { "ID 列表不能为空" }
        require(ids.all { it > 0 }) { "ID 必须大于 0" }
        val distinctIds = ids.distinct()
        val recordsById = carMasterInfoRepository.findAllById(distinctIds).associateBy { entityId(it) }
        val missingIds = distinctIds.filterNot(recordsById::containsKey)
        require(missingIds.isEmpty()) { "部分记录不存在: ${missingIds.joinToString(",")}" }
        return ids.map { recordsById.getValue(it) }
    }

    @Transactional
    override fun list(page: Int, pageSize: Int): Page<CarMasterInfo> {
        require(page >= 1) { "页码必须大于 0" }
        require(pageSize in 1..100) { "每页数量必须在 1 到 100 之间" }
        return carMasterInfoRepository.findAll(PageRequest.of(page - 1, pageSize))
    }

    @Transactional
    override fun update(id: Long, entity: CarMasterInfo): CarMasterInfo {
        require(id > 0) { "ID 必须大于 0" }
        require(entityId(entity) == id) { "路径 ID 必须与请求体 ID 一致" }
        val current = carMasterInfoRepository.findById(id)
            .orElseThrow { IllegalArgumentException("记录不存在: $id") }
        copyEditableProperties(entity, current)
        return carMasterInfoRepository.save(current)
    }

    @Transactional
    override fun updateBatch(entities: List<CarMasterInfo>): List<CarMasterInfo> {
        require(entities.isNotEmpty()) { "更新列表不能为空" }
        val ids = entities.map { entityId(it) }
        require(ids.all { it != null && it > 0 }) { "更新记录必须提供有效 ID" }
        require(ids.distinct().size == ids.size) { "更新记录的 ID 不能重复" }
        val currentById = carMasterInfoRepository.findAllById(ids.filterNotNull()).associateBy { entityId(it) }
        val missingIds = ids.filterNotNull().filterNot(currentById::containsKey)
        require(missingIds.isEmpty()) { "部分记录不存在: ${missingIds.joinToString(",")}" }
        val updated = entities.map { incoming ->
            val current = currentById.getValue(entityId(incoming))
            copyEditableProperties(incoming, current)
            current
        }
        return carMasterInfoRepository.saveAll(updated)
    }

    @Transactional
    override fun delete(id: Long) {
        require(id > 0) { "ID 必须大于 0" }
        require(carMasterInfoRepository.existsById(id)) { "记录不存在: $id" }
        carMasterInfoRepository.deleteById(id)
    }

    @Transactional
    override fun deleteBatch(ids: List<Long>) {
        require(ids.isNotEmpty()) { "ID 列表不能为空" }
        require(ids.all { it > 0 }) { "ID 必须大于 0" }
        val distinctIds = ids.distinct()
        val records = carMasterInfoRepository.findAllById(distinctIds)
        val recordsById = records.associateBy { entityId(it) }
        val missingIds = distinctIds.filterNot(recordsById::containsKey)
        require(missingIds.isEmpty()) { "部分记录不存在: ${missingIds.joinToString(",")}" }
        carMasterInfoRepository.deleteAll(distinctIds.map(recordsById::getValue))
    }

    @Transactional
    override fun getAllList(): List<CarMasterInfo> = carMasterInfoRepository.findAll()

    private fun entityId(entity: CarMasterInfo): Long? {
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

    private fun copyEditableProperties(source: CarMasterInfo, target: CarMasterInfo) {
        val sourceWrapper = BeanWrapperImpl(source)
        val targetWrapper = BeanWrapperImpl(target)
        val protectedProperties = setOf("id", "createdAt", "updatedAt", "updateTime", "passwordHash")
        targetWrapper.propertyDescriptors
            .asSequence()
            .map { it.name }
            .filterNot(protectedProperties::contains)
            .filter { sourceWrapper.isReadableProperty(it) && targetWrapper.isWritableProperty(it) }
            .forEach { property -> targetWrapper.setPropertyValue(property, sourceWrapper.getPropertyValue(property)) }
    }
}
