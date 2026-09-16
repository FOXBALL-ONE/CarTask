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


@RestController
@RequestMapping("/api/departments")
/** class DepartmentScopeController：Web 控制器，负责接收 HTTP 请求、调用领域服务并构造统一响应。 */
class DepartmentScopeController(
    private val backfillService: DepartmentLinkBackfillService,
    private val responseBuilder: ResponseBuilder,
) {
    
    @PostMapping("/backfill-links")
    @PreAuthorize("hasRole('SUPER_ADMIN') and hasAuthority('department:manage')")
            /** backfillLinks：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
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
    
    
    @GetMapping("/unlinked-summary")
    @PreAuthorize("hasRole('SUPER_ADMIN') and hasAuthority('department:manage')")
            /** unlinkedSummary：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
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
