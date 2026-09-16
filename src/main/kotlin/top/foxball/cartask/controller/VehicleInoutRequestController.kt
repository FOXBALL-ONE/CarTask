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


data class VehicleInoutRequestNewOwnerBody(
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("phone") val phone: String? = null,
    @param:JsonProperty("department_id") val departmentId: Long? = null,
    @param:JsonProperty("job_title") val jobTitle: String? = null,
    
    @param:JsonProperty("password") val password: String? = null,
)


data class VehicleInoutRequestCreateBody(
    @param:JsonProperty("plate") val plate: String? = null,
    @param:JsonProperty("card_name") val cardName: String? = null,
    @param:JsonProperty("area_code") val areaCode: String? = null,
    @param:JsonProperty("valid_from") val validFrom: String? = null,
    @param:JsonProperty("valid_to") val validTo: String? = null,
    @param:JsonProperty("new_owner") val newOwner: VehicleInoutRequestNewOwnerBody? = null,
)


/** class VehicleInoutRequestUpdateBody：Web 控制器，负责接收 HTTP 请求、调用领域服务并构造统一响应。 */
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
    
    
    @get:JsonIgnore
    @set:JsonIgnore
    var areaCodeProvided: Boolean = false
}


data class VehicleInoutRequestReviewBody(
    @param:JsonProperty("approved") val approved: Boolean? = null,
    @param:JsonProperty("reason") val reason: String? = null,
)

data class VehicleInoutRequestCancelBody(
    @param:JsonProperty("reason") val reason: String? = null,
)


@RestController
@RequestMapping("/api/vehicle-inout-requests")
/** class VehicleInoutRequestController：Web 控制器，负责接收 HTTP 请求、调用领域服务并构造统一响应。 */
class VehicleInoutRequestController(
    private val service: VehicleInoutRequestService,
    private val responseBuilder: ResponseBuilder,
) {
    
    @GetMapping
    @PreAuthorize("hasAuthority('vehicle-inout-request:read')")
            /** list：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
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
    
    
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('vehicle-inout-request:read')")
            /** get：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
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
    
    
    @PostMapping
    @PreAuthorize("hasAuthority('vehicle-inout-request:apply')")
            /** create：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
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
    
    
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('vehicle-inout-request:apply')")
            /** update：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
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
                areaCode = if (body.areaCodeProvided) body.areaCode ?: "" else null,
                validFrom = body.validFrom?.let { requireDateTime(it, "有效期开始时间") },
                validTo = body.validTo?.let { requireDateTime(it, "有效期结束时间") },
            ),
        )
        val rs = Response(requireNotNull(request.id), request.status.value())
        return responseBuilder.ok().message("申请已更新").data(rs).build()
    }
    
    
    @PutMapping("/{id}/review")
    @PreAuthorize("hasAuthority('vehicle-inout-request:review')")
            /** review：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
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
    
    
    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('vehicle-inout-request:apply')")
            /** cancel：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
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
    
    
    @PutMapping("/{id}/sync")
    @PreAuthorize("hasAuthority('vehicle-inout-request:sync')")
            /** synchronize：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
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
    
    
    private fun parseStatus(raw: String?): VehicleInoutRequest.Status? {
        val value = raw?.trim()?.takeIf(String::isNotEmpty) ?: return null
        return VehicleInoutRequest.Status.entries.firstOrNull { it.name == value || it.value() == value }
            ?: throw IllegalArgumentException("不支持的申请状态：$value")
    }
    
    
    private fun parseSyncStatus(raw: String?): VehicleInoutRequest.SyncStatus? {
        val value = raw?.trim()?.takeIf(String::isNotEmpty) ?: return null
        return VehicleInoutRequest.SyncStatus.entries.firstOrNull { it.name == value || it.value() == value }
            ?: throw IllegalArgumentException("不支持的下发状态：$value")
    }
    
    
    private fun requireDateTime(raw: String?, label: String): LocalDateTime {
        val value = raw?.trim()?.takeIf(String::isNotEmpty)
            ?: throw IllegalArgumentException("${label}不能为空")
        return parseDateTime(value) ?: throw IllegalArgumentException("${label}格式不正确：$raw")
    }
    
    
    private fun parseDateTime(value: String): LocalDateTime? {
        runCatching { LocalDateTime.parse(value) }.getOrNull()?.let { return it }
        runCatching { OffsetDateTime.parse(value).toLocalDateTime() }.getOrNull()?.let { return it }
        return runCatching { LocalDate.parse(value) }.getOrNull()?.atStartOfDay()
    }
}
