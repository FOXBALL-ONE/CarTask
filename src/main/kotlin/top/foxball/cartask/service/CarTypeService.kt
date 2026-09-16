package top.foxball.cartask.service

import org.springframework.data.domain.Page
import top.foxball.cartask.entity.type.CarType


interface CarTypeService {
    
    
    fun create(entity: CarType): CarType
    
    
    fun createBatch(entities: List<CarType>): List<CarType>
    
    
    fun get(id: Long): CarType
    
    
    fun getBatch(ids: List<Long>): List<CarType>
    
    
    fun list(page: Int, pageSize: Int): Page<CarType>
    
    
    fun update(id: Long, entity: CarType): CarType
    
    
    fun updateBatch(entities: List<CarType>): List<CarType>
    
    
    fun delete(id: Long)
    
    
    fun deleteBatch(ids: List<Long>)
}
