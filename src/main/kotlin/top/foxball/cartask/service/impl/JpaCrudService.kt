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
    @Transactional
    override fun create(entity: T): T = repository.save(entity)

    @Transactional
    override fun createBatch(entities: List<T>): List<T> {
        require(entities.isNotEmpty()) { "创建列表不能为空" }
        return repository.saveAll(entities)
    }

    @Transactional
    override fun get(id: Long): T = repository.findById(id)
        .orElseThrow { IllegalArgumentException("记录不存在: $id") }

    @Transactional
    override fun getBatch(ids: List<Long>): List<T> {
        require(ids.isNotEmpty()) { "ID 列表不能为空" }
        val distinctIds = ids.distinct()
        val records = repository.findAllById(distinctIds)
        require(records.size == distinctIds.size) { "部分记录不存在" }
        return records
    }

    @Transactional
    override fun list(page: Int, pageSize: Int): Page<T> {
        require(page >= 1) { "页码必须大于 0" }
        require(pageSize in 1..100) { "每页数量必须在 1 到 100 之间" }
        return repository.findAll(PageRequest.of(page - 1, pageSize))
    }

    @Transactional
    override fun update(id: Long, entity: T): T {
        require(repository.existsById(id)) { "记录不存在: $id" }
        return repository.save(entity)
    }

    @Transactional
    override fun updateBatch(entities: List<T>): List<T> {
        require(entities.isNotEmpty()) { "更新列表不能为空" }
        return repository.saveAll(entities)
    }

    @Transactional
    override fun delete(id: Long) {
        require(repository.existsById(id)) { "记录不存在: $id" }
        repository.deleteById(id)
    }

    @Transactional
    override fun deleteBatch(ids: List<Long>) {
        require(ids.isNotEmpty()) { "ID 列表不能为空" }
        repository.deleteAllById(ids.distinct())
    }
}
