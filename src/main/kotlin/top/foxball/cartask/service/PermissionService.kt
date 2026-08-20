package top.foxball.cartask.service

import top.foxball.cartask.entity.Permission
import org.springframework.data.domain.Page

/** 权限字典的业务服务。 */
interface PermissionService {
    fun create(entity: Permission): Permission
    fun createBatch(entities: List<Permission>): List<Permission>
    fun get(id: Long): Permission
    fun getBatch(ids: List<Long>): List<Permission>
    fun list(page: Int, pageSize: Int): Page<Permission>
    fun update(id: Long, entity: Permission): Permission
    fun updateBatch(entities: List<Permission>): List<Permission>
    fun delete(id: Long)
    fun deleteBatch(ids: List<Long>)
}
