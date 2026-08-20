package top.foxball.cartask.service

import top.foxball.cartask.entity.type.ZoneType
import org.springframework.data.domain.Page

/** 区域类型字典的业务服务。 */
interface ZoneTypeService {
    fun create(entity: ZoneType): ZoneType
    fun createBatch(entities: List<ZoneType>): List<ZoneType>
    fun get(id: Long): ZoneType
    fun getBatch(ids: List<Long>): List<ZoneType>
    fun list(page: Int, pageSize: Int): Page<ZoneType>
    fun update(id: Long, entity: ZoneType): ZoneType
    fun updateBatch(entities: List<ZoneType>): List<ZoneType>
    fun delete(id: Long)
    fun deleteBatch(ids: List<Long>)
}
