package top.foxball.cartask.service

import java.time.LocalDateTime
import org.springframework.data.domain.Page
import top.foxball.cartask.entity.VehicleInoutRequest

/** 车辆进出申请登记的业务服务。 */
interface VehicleInoutRequestService {

    /**
     * 登记申请时顺带新建的车主与该车主的平台账号。
     *
     * 全部非空：这几个字段在建档路径上都是必填，缺了会造出一条挂不上车主或没有部门的半成品数据。
     * [password] 是唯一可缺省的一项，缺省时下发初始密码并要求首次登录改密。
     */
    data class NewOwnerCommand(
        val name: String,
        val phone: String,
        val departmentId: Long,
        val jobTitle: String,
        val password: String?,
    )

    data class CreateCommand(
        val plate: String,
        val cardName: String,
        val areaCode: String?,
        val validFrom: LocalDateTime,
        val validTo: LocalDateTime,
        /** 非空表示车牌尚未建档，连同车主与账号一起新建。 */
        val newOwner: NewOwnerCommand? = null,
    )

    /** 申请单的可编辑字段；null 表示本次不改该字段。 */
    data class UpdateCommand(
        val plate: String? = null,
        val cardName: String? = null,
        val areaCode: String? = null,
        val validFrom: LocalDateTime? = null,
        val validTo: LocalDateTime? = null,
    )

    /** 列表查询条件。 */
    data class ListFilter(
        val keyword: String? = null,
        val status: VehicleInoutRequest.Status? = null,
        val syncStatus: VehicleInoutRequest.SyncStatus? = null,
        val startDate: java.time.LocalDate? = null,
        val endDate: java.time.LocalDate? = null,
    )

    /** 下发结果，供接口直接回给前端。 */
    data class SyncOutcome(
        val synced: Boolean,
        val message: String,
        val cardId: Long?,
    )

    fun list(filter: ListFilter, page: Int, pageSize: Int): Page<VehicleInoutRequest>

    fun get(id: Long): VehicleInoutRequest

    fun create(command: CreateCommand): VehicleInoutRequest

    fun update(id: Long, command: UpdateCommand): VehicleInoutRequest

    fun review(id: Long, approved: Boolean, reason: String?): VehicleInoutRequest

    fun cancel(id: Long, reason: String?): VehicleInoutRequest

    fun synchronize(id: Long): SyncOutcome
}
