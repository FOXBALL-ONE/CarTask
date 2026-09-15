package top.foxball.cartask.controller

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import top.foxball.cartask.entity.VehicleInoutRequest
import top.foxball.cartask.service.VehicleInoutRequestService
import top.foxball.cartask.shared.Response
import top.foxball.cartask.shared.ResponseBuilder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime

/**
 * 登记申请时顺带新建的车主与账号。
 *
 * 与 `plate` 一起出现时才生效：`plate` 必须尚未建档，后端会把它作为新车牌建档。
 */
data class VehicleInoutRequestNewOwnerBody(
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("phone") val phone: String? = null,
    @param:JsonProperty("department_id") val departmentId: Long? = null,
    @param:JsonProperty("job_title") val jobTitle: String? = null,
    /** 留空则下发初始密码，并要求首次登录改密。 */
    @param:JsonProperty("password") val password: String? = null,
)

/** 登记一条车辆进出申请。`plate` 必须已建档；带 `new_owner` 时改为连车牌一起新建。 */
data class VehicleInoutRequestCreateBody(
    @param:JsonProperty("plate") val plate: String? = null,
    @param:JsonProperty("card_name") val cardName: String? = null,
    @param:JsonProperty("area_code") val areaCode: String? = null,
    @param:JsonProperty("valid_from") val validFrom: String? = null,
    @param:JsonProperty("valid_to") val validTo: String? = null,
    @param:JsonProperty("new_owner") val newOwner: VehicleInoutRequestNewOwnerBody? = null,
)

/**
 * 编辑一条待审核的申请；字段缺省表示不改。
 *
 * `area_code` 特殊：它是唯一的可清空字段，所以用「字段是否出现」区分意图——
 * 不传表示保持原区域，传空串表示清空。用 null 表示清空会和「字段缺省」撞在一起。
 */
class VehicleInoutRequestUpdateBody {
    @JsonProperty("plate")
    var plate: String? = null

    @JsonProperty("card_name")
    var cardName: String? = null

    @JsonProperty("area_code")
    var areaCode: String? = null
        set(value) {
            field = value
            areaCodeProvided = true
        }

    @JsonProperty("valid_from")
    var validFrom: String? = null

    @JsonProperty("valid_to")
    var validTo: String? = null

    /** 请求里是否出现过 area_code；见类注释，null 与缺省在这里是两件事。 */
    @get:JsonIgnore
    @set:JsonIgnore
    var areaCodeProvided: Boolean = false
}

/** 审核结论。驳回必须给理由，否则申请人无从知道被拒的原因。 */
data class VehicleInoutRequestReviewBody(
    @param:JsonProperty("approved") val approved: Boolean? = null,
    @param:JsonProperty("reason") val reason: String? = null,
)

data class VehicleInoutRequestCancelBody(
    @param:JsonProperty("reason") val reason: String? = null,
)

/** 车辆进出申请登记接口：登记 → 审核 → 下发科拓月卡。 */
@RestController
@RequestMapping("/api/vehicle-inout-requests")
class VehicleInoutRequestController(
    private val service: VehicleInoutRequestService,
    private val responseBuilder: ResponseBuilder,
) {
    /** 分页查询申请单；只返回当前数据范围内的记录。 */
    @GetMapping
    @PreAuthorize("hasAuthority('vehicle-inout-request:read')")
    fun list(
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(name = "sync_status", required = false) syncStatus: String?,
        @RequestParam(name = "start_date", required = false) startDate: LocalDate?,
        @RequestParam(name = "end_date", required = false) endDate: LocalDate?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(name = "page_size", defaultValue = "8") pageSize: Int,
    ): ResponseEntity<Response> {
        data class RequestData(
            val id: Long,
            val plate: String,
            val owner: String,
            @param:JsonProperty("owner_id") val ownerId: Long,
            val phone: String,
            val dept: String,
            @param:JsonProperty("card_name") val cardName: String,
            @param:JsonProperty("area_code") val areaCode: String?,
            @param:JsonProperty("area_name") val areaName: String?,
            @param:JsonProperty("valid_from") val validFrom: LocalDateTime,
            @param:JsonProperty("valid_to") val validTo: LocalDateTime,
            val status: String,
            @param:JsonProperty("sync_status") val syncStatus: String,
            @param:JsonProperty("sync_message") val syncMessage: String?,
            @param:JsonProperty("card_id") val cardId: Long?,
            @param:JsonProperty("apply_time") val applyTime: LocalDateTime,
            @param:JsonProperty("reviewed_by") val reviewedBy: String?,
            @param:JsonProperty("reviewed_at") val reviewedAt: LocalDateTime?,
            @param:JsonProperty("review_reason") val reviewReason: String?,
            @param:JsonProperty("synced_at") val syncedAt: LocalDateTime?,
        )

        data class PageData(val items: List<RequestData>, val total: Int)

        val result = service.list(
            VehicleInoutRequestService.ListFilter(
                keyword = keyword,
                status = parseStatus(status),
                syncStatus = parseSyncStatus(syncStatus),
                startDate = startDate,
                endDate = endDate,
            ),
            page,
            pageSize,
        )
        val items = result.content.map {
            RequestData(
                requireNotNull(it.id),
                it.plate,
                it.owner,
                it.ownerId,
                it.phone,
                it.dept,
                it.cardName,
                it.areaCode,
                it.areaName,
                it.validFrom,
                it.validTo,
                it.status.value(),
                it.syncStatus.value(),
                it.syncMessage,
                it.cardId,
                it.applyTime,
                it.reviewedBy,
                it.reviewedAt,
                it.reviewReason,
                it.syncedAt,
            )
        }
        return responseBuilder.ok().data(PageData(items, result.totalElements.toInt())).build()
    }

    /** 按 ID 取申请单详情。 */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('vehicle-inout-request:read')")
    fun get(@PathVariable id: Long): ResponseEntity<Response> {
        data class RequestData(
            val id: Long,
            val plate: String,
            val owner: String,
            @param:JsonProperty("owner_id") val ownerId: Long,
            val phone: String,
            val dept: String,
            @param:JsonProperty("card_name") val cardName: String,
            @param:JsonProperty("area_code") val areaCode: String?,
            @param:JsonProperty("area_name") val areaName: String?,
            @param:JsonProperty("valid_from") val validFrom: LocalDateTime,
            @param:JsonProperty("valid_to") val validTo: LocalDateTime,
            val status: String,
            @param:JsonProperty("sync_status") val syncStatus: String,
            @param:JsonProperty("sync_message") val syncMessage: String?,
            @param:JsonProperty("card_id") val cardId: Long?,
            @param:JsonProperty("apply_time") val applyTime: LocalDateTime,
            @param:JsonProperty("reviewed_by") val reviewedBy: String?,
            @param:JsonProperty("reviewed_at") val reviewedAt: LocalDateTime?,
            @param:JsonProperty("review_reason") val reviewReason: String?,
            @param:JsonProperty("synced_at") val syncedAt: LocalDateTime?,
        )

        val request = service.get(id)
        val rs = RequestData(
            requireNotNull(request.id),
            request.plate,
            request.owner,
            request.ownerId,
            request.phone,
            request.dept,
            request.cardName,
            request.areaCode,
            request.areaName,
            request.validFrom,
            request.validTo,
            request.status.value(),
            request.syncStatus.value(),
            request.syncMessage,
            request.cardId,
            request.applyTime,
            request.reviewedBy,
            request.reviewedAt,
            request.reviewReason,
            request.syncedAt,
        )
        return responseBuilder.ok().data(rs).build()
    }

    /** 登记一条申请。 */
    @PostMapping
    @PreAuthorize("hasAuthority('vehicle-inout-request:apply')")
    fun create(@RequestBody body: VehicleInoutRequestCreateBody): ResponseEntity<Response> {
        data class Response(
            val id: Long,
            val plate: String,
            val status: String,
        )

        val request = service.create(
            VehicleInoutRequestService.CreateCommand(
                plate = requireNotNull(body.plate) { "车牌号不能为空" },
                cardName = requireNotNull(body.cardName) { "月卡名称不能为空" },
                areaCode = body.areaCode,
                validFrom = requireDateTime(body.validFrom, "有效期开始时间"),
                validTo = requireDateTime(body.validTo, "有效期结束时间"),
                newOwner = body.newOwner?.let { newOwner ->
                    VehicleInoutRequestService.NewOwnerCommand(
                        name = requireNotNull(newOwner.name) { "车主姓名不能为空" },
                        phone = requireNotNull(newOwner.phone) { "车主手机号不能为空" },
                        departmentId = requireNotNull(newOwner.departmentId) { "部门不能为空" },
                        jobTitle = requireNotNull(newOwner.jobTitle) { "职务不能为空" },
                        password = newOwner.password,
                    )
                },
            ),
        )
        val rs = Response(requireNotNull(request.id), request.plate, request.status.value())
        return responseBuilder.created().message("申请已提交").data(rs).build()
    }

    /** 编辑待审核的申请。 */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('vehicle-inout-request:apply')")
    fun update(
        @PathVariable id: Long,
        @RequestBody body: VehicleInoutRequestUpdateBody,
    ): ResponseEntity<Response> {
        data class Response(
            val id: Long,
            val status: String,
        )

        val request = service.update(
            id,
            VehicleInoutRequestService.UpdateCommand(
                plate = body.plate,
                cardName = body.cardName,
                // 只在请求里出现过 area_code 时才交给服务层：null 与「缺省」在这里是两件事。
                areaCode = if (body.areaCodeProvided) body.areaCode ?: "" else null,
                validFrom = body.validFrom?.let { requireDateTime(it, "有效期开始时间") },
                validTo = body.validTo?.let { requireDateTime(it, "有效期结束时间") },
            ),
        )
        val rs = Response(requireNotNull(request.id), request.status.value())
        return responseBuilder.ok().message("申请已更新").data(rs).build()
    }

    /** 审核申请。通过后申请单进入待下发状态，科拓调用走下发接口单独触发。 */
    @PutMapping("/{id}/review")
    @PreAuthorize("hasAuthority('vehicle-inout-request:review')")
    fun review(
        @PathVariable id: Long,
        @RequestBody body: VehicleInoutRequestReviewBody,
    ): ResponseEntity<Response> {
        data class Response(
            val id: Long,
            val status: String,
        )

        val approved = requireNotNull(body.approved) { "必须指定审核结论" }
        val request = service.review(id, approved, body.reason)
        val rs = Response(requireNotNull(request.id), request.status.value())
        return responseBuilder.ok()
            .message(if (approved) "审核通过" else "已驳回")
            .data(rs)
            .build()
    }

    /** 撤销一条尚未下发的申请。 */
    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('vehicle-inout-request:apply')")
    fun cancel(
        @PathVariable id: Long,
        @RequestBody body: VehicleInoutRequestCancelBody,
    ): ResponseEntity<Response> {
        data class Response(
            val id: Long,
            val status: String,
        )

        val request = service.cancel(id, body.reason)
        val rs = Response(requireNotNull(request.id), request.status.value())
        return responseBuilder.ok().message("申请已撤销").data(rs).build()
    }

    /**
     * 把已通过的申请下发给科拓。
     *
     * 与审核分开成一个显式动作：科拓调用会失败、会超时，把它和审批结论捆在一起，
     * 一次网络抖动就会丢掉一条已经做出的审批决定。
     */
    @PutMapping("/{id}/sync")
    @PreAuthorize("hasAuthority('vehicle-inout-request:sync')")
    fun synchronize(@PathVariable id: Long): ResponseEntity<Response> {
        data class Response(
            val synced: Boolean,
            val message: String,
            @param:JsonProperty("card_id") val cardId: Long?,
        )

        val outcome = service.synchronize(id)
        val rs = Response(outcome.synced, outcome.message, outcome.cardId)
        return responseBuilder.ok()
            .message(if (outcome.synced) "月卡已下发" else "月卡下发失败")
            .data(rs)
            .build()
    }

    /**
     * parseStatus：执行当前模块中的业务操作。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param raw 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    private fun parseStatus(raw: String?): VehicleInoutRequest.Status? {
        val value = raw?.trim()?.takeIf(String::isNotEmpty) ?: return null
        // 前端传的是界面上的中文文案，也接受枚举名，避免调用方被迫知道后端枚举。
        return VehicleInoutRequest.Status.entries.firstOrNull { it.name == value || it.value() == value }
            ?: throw IllegalArgumentException("不支持的申请状态：$value")
    }

    /**
     * parseSyncStatus：执行当前模块中的业务操作。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param raw 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    private fun parseSyncStatus(raw: String?): VehicleInoutRequest.SyncStatus? {
        val value = raw?.trim()?.takeIf(String::isNotEmpty) ?: return null
        return VehicleInoutRequest.SyncStatus.entries.firstOrNull { it.name == value || it.value() == value }
            ?: throw IllegalArgumentException("不支持的下发状态：$value")
    }

    /**
     * 解析前端传来的有效期时间。
     *
     * 兼容 `datetime-local` 的 `2026-09-14T10:00`、带秒的 ISO 本地时间、以及带偏移量的写法。
     * 带偏移量的一律**按字面时间取用**，不做时区换算——月卡有效期是车场所在时区的墙上时间，
     * 换算一次就会把有效期整体挪几个小时。
     */
    private fun requireDateTime(raw: String?, label: String): LocalDateTime {
        val value = raw?.trim()?.takeIf(String::isNotEmpty)
            ?: throw IllegalArgumentException("${label}不能为空")
        return parseDateTime(value) ?: throw IllegalArgumentException("${label}格式不正确：$raw")
    }

    /**
     * parseDateTime：执行当前模块中的业务操作。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param value 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    private fun parseDateTime(value: String): LocalDateTime? {
        runCatching { LocalDateTime.parse(value) }.getOrNull()?.let { return it }
        runCatching { OffsetDateTime.parse(value).toLocalDateTime() }.getOrNull()?.let { return it }
        // 只填了日期（2026-09-14）时按当天零点算，与 datetime-local 的清空语义一致。
        return runCatching { LocalDate.parse(value) }.getOrNull()?.atStartOfDay()
    }
}
