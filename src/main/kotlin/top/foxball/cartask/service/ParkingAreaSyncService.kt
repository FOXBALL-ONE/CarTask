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
    /**
     * synchronize：执行数据同步、探测或文件处理。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun synchronize(): ParkingAreaSyncResult
}
