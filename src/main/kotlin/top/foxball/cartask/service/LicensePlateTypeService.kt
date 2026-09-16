package top.foxball.cartask.service

import org.springframework.data.domain.Page
import top.foxball.cartask.entity.type.LicensePlateType


interface LicensePlateTypeService {
    
    
    fun create(entity: LicensePlateType): LicensePlateType
    
    
    fun createBatch(entities: List<LicensePlateType>): List<LicensePlateType>
    
    
    fun get(id: Long): LicensePlateType
    
    
    fun getBatch(ids: List<Long>): List<LicensePlateType>
    
    
    fun list(page: Int, pageSize: Int): Page<LicensePlateType>
    
    
    fun update(id: Long, entity: LicensePlateType): LicensePlateType
    
    
    fun updateBatch(entities: List<LicensePlateType>): List<LicensePlateType>
    
    
    fun delete(id: Long)
    
    
    fun deleteBatch(ids: List<Long>)
}
