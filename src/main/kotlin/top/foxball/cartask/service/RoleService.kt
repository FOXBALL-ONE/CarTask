package top.foxball.cartask.service

import org.springframework.data.domain.Page
import top.foxball.cartask.entity.Role


interface RoleService {
    
    
    fun create(entity: Role): Role
    
    
    fun createBatch(entities: List<Role>): List<Role>
    
    
    fun get(id: Long): Role
    
    
    fun getBatch(ids: List<Long>): List<Role>
    
    
    fun list(page: Int, pageSize: Int): Page<Role>
    
    
    fun update(id: Long, entity: Role): Role
    
    
    fun updateBatch(entities: List<Role>): List<Role>
    
    
    fun delete(id: Long)
    
    
    fun deleteBatch(ids: List<Long>)
}
