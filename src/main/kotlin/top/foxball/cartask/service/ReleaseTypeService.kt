package top.foxball.cartask.service

import top.foxball.cartask.entity.type.ReleaseType
import org.springframework.data.domain.Page

/** 放行类型字典的业务服务。 */
interface ReleaseTypeService {
    fun create(entity: ReleaseType): ReleaseType
    fun createBatch(entities: List<ReleaseType>): List<ReleaseType>
    fun get(id: Long): ReleaseType
    fun getBatch(ids: List<Long>): List<ReleaseType>
    fun list(page: Int, pageSize: Int): Page<ReleaseType>
    fun update(id: Long, entity: ReleaseType): ReleaseType
    fun updateBatch(entities: List<ReleaseType>): List<ReleaseType>
    fun delete(id: Long)
    fun deleteBatch(ids: List<Long>)
}
