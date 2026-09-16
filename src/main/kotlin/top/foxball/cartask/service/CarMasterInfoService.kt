package top.foxball.cartask.service

import org.springframework.data.domain.Page
import top.foxball.cartask.entity.CarMasterInfo


interface CarMasterInfoService {
    
    
    fun create(entity: CarMasterInfo): CarMasterInfo
    
    
    fun createBatch(entities: List<CarMasterInfo>): List<CarMasterInfo>
    
    
    fun get(id: Long): CarMasterInfo
    
    
    fun getBatch(ids: List<Long>): List<CarMasterInfo>
    
    
    fun list(page: Int, pageSize: Int): Page<CarMasterInfo>
    
    
    fun update(id: Long, entity: CarMasterInfo): CarMasterInfo
    
    
    fun updateBatch(entities: List<CarMasterInfo>): List<CarMasterInfo>
    
    
    fun delete(id: Long)
    
    
    fun deleteBatch(ids: List<Long>)
    
    
    fun getAllList(): List<CarMasterInfo>
}
