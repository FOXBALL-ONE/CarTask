package top.foxball.cartask.service.impl

import jakarta.transaction.Transactional
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.jpa.repository.JpaRepository
import top.foxball.cartask.service.CrudService

/** 基于 Spring Data JPA 的通用 CRUD 实现。 */
abstract class JpaCrudService<T : Any>(
    private val repository: JpaRepository<T, Long>,
) : CrudService<T> {
    /** 保存一条新实体记录。 */
    @Transactional
    override fun create(entity: T): T = repository.save(entity)

    /** 校验非空后批量保存实体记录。 */
    @Transactional
    override fun createBatch(entities: List<T>): List<T> {
        require(entities.isNotEmpty()) { "创建列表不能为空" }
        return repository.saveAll(entities)
    }

    /** 查询不存在时抛出参数错误。 */
    @Transactional
    override fun get(id: Long): T = repository.findById(id)
        .orElseThrow { IllegalArgumentException("记录不存在: $id") }

    /** 批量查询并确保所有请求的记录都存在。 */
    @Transactional
    override fun getBatch(ids: List<Long>): List<T> {
        require(ids.isNotEmpty()) { "ID 列表不能为空" }
        val distinctIds = ids.distinct()
        val records = repository.findAllById(distinctIds)
        require(records.size == distinctIds.size) { "部分记录不存在" }
        return records
    }

    /** 按页码和每页数量查询实体记录。 */
    @Transactional
    override fun list(page: Int, pageSize: Int): Page<T> {
        require(page >= 1) { "页码必须大于 0" }
        require(pageSize in 1..100) { "每页数量必须在 1 到 100 之间" }
        return repository.findAll(PageRequest.of(page - 1, pageSize))
    }

    /** 确认记录存在后保存更新内容。 */
    @Transactional
    override fun update(id: Long, entity: T): T {
        require(repository.existsById(id)) { "记录不存在: $id" }
        return repository.save(entity)
    }

    /** 校验非空后批量保存更新内容。 */
    @Transactional
    override fun updateBatch(entities: List<T>): List<T> {
        require(entities.isNotEmpty()) { "更新列表不能为空" }
        return repository.saveAll(entities)
    }

    /** 确认记录存在后删除。 */
    @Transactional
    override fun delete(id: Long) {
        require(repository.existsById(id)) { "记录不存在: $id" }
        repository.deleteById(id)
    }

    /** 按去重后的 ID 集合批量删除记录。 */
    @Transactional
    override fun deleteBatch(ids: List<Long>) {
        require(ids.isNotEmpty()) { "ID 列表不能为空" }
        repository.deleteAllById(ids.distinct())
    }
}
