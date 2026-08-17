package top.foxball.cartask.service

import org.springframework.data.domain.Page

/** 提供实体的单条和批量持久化能力。 */
interface CrudService<T : Any> {
    /** 创建一条实体记录。 */
    fun create(entity: T): T
    /** 批量创建实体记录。 */
    fun createBatch(entities: List<T>): List<T>
    /** 按主键查询实体记录。 */
    fun get(id: Long): T
    /** 按多个主键批量查询实体记录。 */
    fun getBatch(ids: List<Long>): List<T>
    /** 分页查询实体记录。 */
    fun list(page: Int, pageSize: Int): Page<T>
    /** 更新指定主键的实体记录。 */
    fun update(id: Long, entity: T): T
    /** 批量更新实体记录。 */
    fun updateBatch(entities: List<T>): List<T>
    /** 删除指定主键的实体记录。 */
    fun delete(id: Long)
    /** 批量删除实体记录。 */
    fun deleteBatch(ids: List<Long>)
}
