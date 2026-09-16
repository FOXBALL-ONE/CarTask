package top.foxball.cartask.service

import org.springframework.data.domain.Page
import top.foxball.cartask.entity.type.ReleaseType


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
