package top.foxball.cartask.service

import org.springframework.data.domain.Page
import top.foxball.cartask.entity.Permission


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
