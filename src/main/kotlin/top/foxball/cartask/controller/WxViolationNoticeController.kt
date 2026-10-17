package top.foxball.cartask.controller

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import top.foxball.cartask.authentication.CurrentUserPrincipal
import top.foxball.cartask.entity.ViolationRecord
import top.foxball.cartask.repository.ViolationRecordRepository
import top.foxball.cartask.scope.DataScopeResolver
import top.foxball.cartask.scope.ScopeQuerySupport
import top.foxball.cartask.shared.Response
import top.foxball.cartask.shared.ResponseBuilder

@RestController
@RequestMapping("/api/wx/violation-notices")
class WxViolationNoticeController(
    private val responseBuilder: ResponseBuilder,
    private val violationRecordRepository: ViolationRecordRepository,
    private val dataScopeResolver: DataScopeResolver,
    private val scopeQuerySupport: ScopeQuerySupport,
) {
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    fun listNotices(
        @AuthenticationPrincipal principal: CurrentUserPrincipal,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(name = "page_size", defaultValue = "10") pageSize: Int,
    ): ResponseEntity<Response> {
        require(page >= 1) { "页码必须大于 0" }
        require(pageSize in 1..50) { "每页数量必须在 1 到 50 之间" }

        data class NoticeData(
            val id: Long,
            @param:JsonProperty("subject_number") val subjectNumber: String,
            @param:JsonProperty("subject_name") val subjectName: String,
            @param:JsonProperty("type_name") val typeName: String,
            val score: Int,
            val description: String?,
            val location: String?,
            @param:JsonProperty("violation_time") val violationTime: String,
            val status: String,
            @param:JsonProperty("handling_remark") val handlingRemark: String?,
            @param:JsonProperty("handled_at") val handledAt: String?,
        )
        data class Response(val items: List<NoticeData>, val total: Int)

        // 通报栏只展示已经通过审核的记录，待复核与撤销记录不会公开显示。
        val scope = dataScopeResolver.forPrincipal(principal)
        val records = violationRecordRepository.findAllWithViolationType()
            .filter {
                it.handlingStatus in setOf(ViolationRecord.HandlingStatus.CONFIRMED, ViolationRecord.HandlingStatus.HANDLED) &&
                    scopeQuerySupport.violationSubjectVisible(scope, it.subject)
            }
            .sortedByDescending { it.violationTime }
        val from = ((page - 1) * pageSize).coerceAtMost(records.size)
        val items = records.subList(from, (from + pageSize).coerceAtMost(records.size)).map { record ->
            NoticeData(
                requireNotNull(record.id),
                record.subject.subjectNumber,
                record.subject.subjectName,
                record.violationType.violationName ?: "未分类",
                record.violationFraction,
                record.violationContent,
                record.violationLocation,
                record.violationTime.toString(),
                record.handlingStatus.name,
                record.handlingRemark,
                record.handledAt?.toString(),
            )
        }
        val rs = Response(items, records.size)
        return responseBuilder.ok().data(rs).build()
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    fun getNotice(
        @AuthenticationPrincipal principal: CurrentUserPrincipal,
        @PathVariable id: Long,
    ): ResponseEntity<Response> {
        data class Response(
            val id: Long,
            @param:JsonProperty("subject_number") val subjectNumber: String,
            @param:JsonProperty("subject_name") val subjectName: String,
            @param:JsonProperty("type_name") val typeName: String,
            val score: Int,
            val description: String?,
            val location: String?,
            @param:JsonProperty("violation_time") val violationTime: String,
            val status: String,
            @param:JsonProperty("handling_remark") val handlingRemark: String?,
            @param:JsonProperty("handled_at") val handledAt: String?,
        )

        val record = violationRecordRepository.findById(id).orElseThrow { IllegalArgumentException("通报记录不存在") }
        val scope = dataScopeResolver.forPrincipal(principal)
        require(
            record.handlingStatus in setOf(ViolationRecord.HandlingStatus.CONFIRMED, ViolationRecord.HandlingStatus.HANDLED) &&
                scopeQuerySupport.violationSubjectVisible(scope, record.subject)
        ) {
            "通报记录不存在"
        }
        val rs = Response(
            requireNotNull(record.id),
            record.subject.subjectNumber,
            record.subject.subjectName,
            record.violationType.violationName ?: "未分类",
            record.violationFraction,
            record.violationContent,
            record.violationLocation,
            record.violationTime.toString(),
            record.handlingStatus.name,
            record.handlingRemark,
            record.handledAt?.toString(),
        )
        return responseBuilder.ok().data(rs).build()
    }
}
