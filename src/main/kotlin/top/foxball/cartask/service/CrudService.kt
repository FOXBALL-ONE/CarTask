package top.foxball.cartask.service

import org.springframework.data.domain.Page

/** 提供实体的单条和批量持久化能力。 */
interface CrudService<T : Any> {
    fun create(entity: T): T
    fun createBatch(entities: List<T>): List<T>
    fun get(id: Long): T
    fun getBatch(ids: List<Long>): List<T>
    fun list(page: Int, pageSize: Int): Page<T>
    fun update(id: Long, entity: T): T
    fun updateBatch(entities: List<T>): List<T>
    fun delete(id: Long)
    fun deleteBatch(ids: List<Long>)
}
