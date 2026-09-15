package top.foxball.cartask.service

import org.springframework.data.domain.Page
import top.foxball.cartask.entity.VehicleInoutRequest
import java.time.LocalDateTime

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

    /**
     * list：查询或读取相关数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param filter 参与本次处理的输入参数。
     * @param page 参与本次处理的输入参数。
     * @param pageSize 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun list(filter: ListFilter, page: Int, pageSize: Int): Page<VehicleInoutRequest>

    /**
     * get：查询或读取相关数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param id 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun get(id: Long): VehicleInoutRequest

    /**
     * create：创建、保存或初始化相关数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param command 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun create(command: CreateCommand): VehicleInoutRequest

    /**
     * update：更新业务状态或修改相关配置。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param id 参与本次处理的输入参数。
     * @param command 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun update(id: Long, command: UpdateCommand): VehicleInoutRequest

    /**
     * review：执行当前模块中的业务操作。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param id 参与本次处理的输入参数。
     * @param approved 参与本次处理的输入参数。
     * @param reason 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun review(id: Long, approved: Boolean, reason: String?): VehicleInoutRequest

    /**
     * cancel：校验输入、状态或访问条件。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param id 参与本次处理的输入参数。
     * @param reason 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun cancel(id: Long, reason: String?): VehicleInoutRequest

    /**
     * synchronize：执行数据同步、探测或文件处理。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param id 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun synchronize(id: Long): SyncOutcome
}
