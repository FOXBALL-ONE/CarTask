package top.foxball.cartask.service

import top.foxball.cartask.entity.AccessRecord
import org.springframework.data.domain.Page

/** 车辆进出记录的业务服务。 */
interface AccessRecordService {
    fun create(entity: AccessRecord): AccessRecord
    fun createBatch(entities: List<AccessRecord>): List<AccessRecord>
    fun get(id: Long): AccessRecord
    fun getBatch(ids: List<Long>): List<AccessRecord>
    fun list(page: Int, pageSize: Int): Page<AccessRecord>
    fun update(id: Long, entity: AccessRecord): AccessRecord
    fun updateBatch(entities: List<AccessRecord>): List<AccessRecord>
    fun delete(id: Long)
    fun deleteBatch(ids: List<Long>)
}
