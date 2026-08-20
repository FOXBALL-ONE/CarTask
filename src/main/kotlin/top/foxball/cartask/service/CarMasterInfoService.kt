package top.foxball.cartask.service

import top.foxball.cartask.entity.CarMasterInfo
import org.springframework.data.domain.Page

/** 车辆主档的业务服务。 */
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
    /** 获取全部车主信息。 */
    fun getAllList(): List<CarMasterInfo>
}
