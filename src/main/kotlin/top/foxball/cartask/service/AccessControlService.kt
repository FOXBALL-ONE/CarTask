package top.foxball.cartask.service

import top.foxball.cartask.entity.AccessControl
import org.springframework.data.domain.Page

/** 门禁授权记录的业务服务。 */
interface AccessControlService {
    fun create(entity: AccessControl): AccessControl
    fun createBatch(entities: List<AccessControl>): List<AccessControl>
    fun get(id: Long): AccessControl
    fun getBatch(ids: List<Long>): List<AccessControl>
    fun list(page: Int, pageSize: Int): Page<AccessControl>
    fun update(id: Long, entity: AccessControl): AccessControl
    fun updateBatch(entities: List<AccessControl>): List<AccessControl>
    fun review(id: Long, approved: Boolean, reason: String): AccessControl
    fun synchronize(id: Long): AccessControl
    fun delete(id: Long)
    fun deleteBatch(ids: List<Long>)
}
