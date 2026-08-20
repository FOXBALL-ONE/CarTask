package top.foxball.cartask.service

import top.foxball.cartask.entity.Device
import org.springframework.data.domain.Page

/** 接入设备的业务服务。 */
interface DeviceService {
    fun create(entity: Device): Device
    fun createBatch(entities: List<Device>): List<Device>
    fun get(id: Long): Device
    fun getBatch(ids: List<Long>): List<Device>
    fun list(page: Int, pageSize: Int): Page<Device>
    fun update(id: Long, entity: Device): Device
    fun updateBatch(entities: List<Device>): List<Device>
    fun delete(id: Long)
    fun deleteBatch(ids: List<Long>)
}
