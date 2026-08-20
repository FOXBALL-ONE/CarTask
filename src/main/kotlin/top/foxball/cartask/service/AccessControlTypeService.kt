package top.foxball.cartask.service

import top.foxball.cartask.entity.type.AccessControlType
import org.springframework.data.domain.Page

/** 门禁授权类型字典的业务服务。 */
interface AccessControlTypeService {
    fun create(entity: AccessControlType): AccessControlType
    fun createBatch(entities: List<AccessControlType>): List<AccessControlType>
    fun get(id: Long): AccessControlType
    fun getBatch(ids: List<Long>): List<AccessControlType>
    fun list(page: Int, pageSize: Int): Page<AccessControlType>
    fun update(id: Long, entity: AccessControlType): AccessControlType
    fun updateBatch(entities: List<AccessControlType>): List<AccessControlType>
    fun delete(id: Long)
    fun deleteBatch(ids: List<Long>)
}
