package top.foxball.cartask.service

import com.fasterxml.jackson.annotation.JsonProperty
import top.foxball.cartask.entity.AccessRecord

/** 车辆进出记录的业务服务。 */
interface AccessRecordService {
    /** 进出记录接口对外返回的数据格式。 */
    data class AccessRecordData(
        val id: Long,
        val plate: String?,
        val owner: String?,
        val dept: String?,
        val time: String,
        val direction: String,
        val gate: String?,
        val vehicleType: String?,
        val passType: String?,
        val passDesc: String?,
        val photo: String?,
    )

    /** 进出记录分页返回数据。 */
    data class PageData(
        val records: List<AccessRecordData>,
        val page: Int,
        @param:JsonProperty("page_size") val pageSize: Int,
        val total: Long,
    )

    fun create(entity: AccessRecord): AccessRecordData
    fun createBatch(entities: List<AccessRecord>): List<AccessRecordData>
    fun get(id: Long): AccessRecordData
    fun getBatch(ids: List<Long>): List<AccessRecordData>
    fun list(page: Int, pageSize: Int): PageData
    fun update(id: Long, entity: AccessRecord): AccessRecordData
    fun updateBatch(entities: List<AccessRecord>): List<AccessRecordData>
    fun delete(id: Long)
    fun deleteBatch(ids: List<Long>)
    fun correct(id: Long, entity: AccessRecord, reason: String): AccessRecordData
    fun correctBatch(entities: List<AccessRecord>, reason: String): List<AccessRecordData>
    fun release(id: Long, reason: String): AccessRecordData
}
