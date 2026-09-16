package top.foxball.cartask.service

import org.springframework.data.domain.Page
import top.foxball.cartask.entity.type.ZoneType


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
