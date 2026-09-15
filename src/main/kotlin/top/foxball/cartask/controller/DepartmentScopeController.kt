package top.foxball.cartask.controller

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import top.foxball.cartask.scope.DepartmentLinkBackfillService
import top.foxball.cartask.scope.UnlinkedSummary
import top.foxball.cartask.shared.Response
import top.foxball.cartask.shared.ResponseBuilder

/**
 * 部门归属回填与未解析统计。
 *
 * 独立于部门 CRUD 单独成类：这两个接口是上线运维动作（先回填、确认统计干净，再开放部门管理），
 * 与部门资料的日常维护不是同一件事，权限也只给超级管理员。
 */
@RestController
@RequestMapping("/api/departments")
class DepartmentScopeController(
    private val backfillService: DepartmentLinkBackfillService,
    private val responseBuilder: ResponseBuilder,
) {
    /** 幂等地把存量数据的部门自由文本与车牌补齐成稳定键。 */
    @PostMapping("/backfill-links")
    @PreAuthorize("hasRole('SUPER_ADMIN') and hasAuthority('department:manage')")
    fun backfillLinks(): ResponseEntity<Response> {
        data class CountData(
            val scanned: Int,
            val resolved: Int,
            val unresolved: Int,
        )

        data class Response(
            val owners: CountData,
            @param:JsonProperty("gate_persons") val gatePersons: CountData,
            @param:JsonProperty("person_records") val personRecords: CountData,
            val spots: CountData,
            @param:JsonProperty("access_records") val accessRecords: CountData,
            @param:JsonProperty("violation_subjects") val violationSubjects: CountData,
        )

        val result = backfillService.backfill()
        val rs = Response(
            CountData(result.owners.scanned, result.owners.resolved, result.owners.unresolved),
            CountData(result.gatePersons.scanned, result.gatePersons.resolved, result.gatePersons.unresolved),
            CountData(result.personRecords.scanned, result.personRecords.resolved, result.personRecords.unresolved),
            CountData(result.spots.scanned, result.spots.resolved, result.spots.unresolved),
            CountData(result.accessRecords.scanned, result.accessRecords.resolved, result.accessRecords.unresolved),
            CountData(
                result.violationSubjects.scanned,
                result.violationSubjects.resolved,
                result.violationSubjects.unresolved,
            ),
        )
        return responseBuilder.ok().message("归属回填完成").data(rs).build()
    }

    /**
     * 仍未解析出部门归属的行数。
     *
     * 这是开放部门管理前的闸门：范围判定是 fail closed 的，数字不为零就意味着这些数据对
     * 受限角色凭空消失，必须先补数据或补部门名称再开通。
     */
    @GetMapping("/unlinked-summary")
    @PreAuthorize("hasRole('SUPER_ADMIN') and hasAuthority('department:manage')")
    fun unlinkedSummary(): ResponseEntity<Response> {
        data class Response(
            val owners: Int,
            @param:JsonProperty("gate_persons") val gatePersons: Int,
            @param:JsonProperty("person_records") val personRecords: Int,
            val spots: Int,
            @param:JsonProperty("access_records") val accessRecords: Int,
            @param:JsonProperty("violation_subjects") val violationSubjects: Int,
        )

        val summary: UnlinkedSummary = backfillService.unlinkedSummary()
        val rs = Response(
            summary.owners,
            summary.gatePersons,
            summary.personRecords,
            summary.spots,
            summary.accessRecords,
            summary.violationSubjects,
        )
        return responseBuilder.ok().data(rs).build()
    }
}
