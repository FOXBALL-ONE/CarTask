package top.foxball.cartask.service

import top.foxball.cartask.entity.type.RestrictionType
import org.springframework.data.domain.Page

/** 限制类型字典的业务服务。 */
interface RestrictionTypeService {
    fun create(entity: RestrictionType): RestrictionType
    fun createBatch(entities: List<RestrictionType>): List<RestrictionType>
    fun get(id: Long): RestrictionType
    fun getBatch(ids: List<Long>): List<RestrictionType>
    fun list(page: Int, pageSize: Int): Page<RestrictionType>
    fun update(id: Long, entity: RestrictionType): RestrictionType
    fun updateBatch(entities: List<RestrictionType>): List<RestrictionType>
    fun delete(id: Long)
    fun deleteBatch(ids: List<Long>)
}
