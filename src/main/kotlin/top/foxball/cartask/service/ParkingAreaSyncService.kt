package top.foxball.cartask.service

/** 停车区域同步结果，用于任务日志和后续手动同步接口展示。 */
data class ParkingAreaSyncResult(
    val receivedCount: Int,
    val createdCount: Int,
    val updatedCount: Int,
    val unchangedCount: Int,
    /** 本次保存的停车场名称，未保存时为 null。 */
    val lotName: String? = null,
    /** 本次响应解析出的车场总车位数量，接口未返回时为 null。 */
    val totalPlaceCount: Int? = null,
)

/** 将科拓停车区域幂等同步到本地区域字典，并保存停车场详情。 */
interface ParkingAreaSyncService {
    fun synchronize(): ParkingAreaSyncResult
}
