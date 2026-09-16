package top.foxball.cartask.service

import org.springframework.data.domain.Page
import top.foxball.cartask.entity.type.RestrictionType


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
