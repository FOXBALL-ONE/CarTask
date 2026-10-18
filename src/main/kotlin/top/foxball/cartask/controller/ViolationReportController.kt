package top.foxball.cartask.controller

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.transaction.Transactional
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import top.foxball.cartask.authentication.CurrentUserPrincipal
import top.foxball.cartask.entity.ViolationReport
import top.foxball.cartask.entity.type.ViolationType
import top.foxball.cartask.repository.ViolationReportRepository
import top.foxball.cartask.repository.ViolationTypeRepository
import top.foxball.cartask.service.FileService
import top.foxball.cartask.shared.Response
import top.foxball.cartask.shared.ResponseBuilder
import java.time.LocalDateTime
import java.util.*

@RestController
@RequestMapping("/api/wx/violation-reports")
class ViolationReportController(
    private val responseBuilder: ResponseBuilder,
    private val violationReportRepository: ViolationReportRepository,
    private val violationTypeRepository: ViolationTypeRepository,
    private val fileService: FileService,
) {
    @GetMapping("/types")
    @PreAuthorize("isAuthenticated()")
    fun listReportTypes(): ResponseEntity<Response> {
        data class TypeData(val id: Long, val name: String, val description: String?)
        data class Response(val items: List<TypeData>)
        
        val items = violationTypeRepository.findAll()
            .filter { it.status == ViolationType.Status.Activity }
            .sortedWith(compareBy<ViolationType> { it.orderNumber }.thenBy { it.id })
            .map { TypeData(requireNotNull(it.id), it.violationName ?: "未命名类型", it.violationContent) }
        val rs = Response(items)
        return responseBuilder.ok().data(rs).build()
    }
    
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    fun listReports(
        @AuthenticationPrincipal principal: CurrentUserPrincipal,
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(name = "page_size", defaultValue = "20") pageSize: Int,
    ): ResponseEntity<Response> {
        require(page >= 1) { "页码必须大于 0" }
        require(pageSize in 1..100) { "每页数量必须在 1 到 100 之间" }
        val normalizedStatus = status?.takeIf(String::isNotBlank)?.uppercase()
        if (normalizedStatus != null) {
            require(runCatching { ViolationReport.Status.valueOf(normalizedStatus) }.isSuccess) { "不支持的举报状态" }
        }
        
        data class ReportData(
            val id: Long,
            @param:JsonProperty("vehicle_number") val vehicleNumber: String,
            @param:JsonProperty("violation_type") val violationType: String,
            @param:JsonProperty("violation_time") val violationTime: String,
            val location: String?,
            val description: String,
            val evidence: String?,
            val status: String,
            @param:JsonProperty("handling_remark") val handlingRemark: String?,
            @param:JsonProperty("handled_at") val handledAt: String?,
            @param:JsonProperty("created_at") val createdAt: String,
        )
        
        data class Response(val items: List<ReportData>, val total: Int)
        
        val reports = violationReportRepository.findByReporterUserIdOrderByCreatedAtDesc(principal.userId)
            .filter { normalizedStatus == null || it.status.name == normalizedStatus }
        val fromIndex = ((page - 1) * pageSize).coerceAtMost(reports.size)
        val items = reports.subList(fromIndex, (fromIndex + pageSize).coerceAtMost(reports.size)).map {
            ReportData(
                requireNotNull(it.id),
                it.vehicleNumber,
                it.violationType,
                it.violationTime.toString(),
                it.violationLocation,
                it.violationContent,
                it.evidenceInfo,
                it.status.name,
                it.handlingRemark,
                it.handledAt?.toString(),
                it.createdAt.toString(),
            )
        }
        val rs = Response(items, reports.size)
        return responseBuilder.ok().data(rs).build()
    }
    
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    fun getReport(
        @AuthenticationPrincipal principal: CurrentUserPrincipal,
        @PathVariable id: Long,
    ): ResponseEntity<Response> {
        val report = violationReportRepository.findById(id)
            .orElseThrow { IllegalArgumentException("举报记录不存在") }
        require(report.reporterUserId == principal.userId) { "举报记录不存在" }
        
        data class Response(
            val id: Long,
            @param:JsonProperty("vehicle_number") val vehicleNumber: String,
            @param:JsonProperty("violation_type") val violationType: String,
            @param:JsonProperty("violation_time") val violationTime: String,
            val location: String?,
            val description: String,
            val evidence: String?,
            val status: String,
            @param:JsonProperty("handling_remark") val handlingRemark: String?,
            @param:JsonProperty("handled_at") val handledAt: String?,
            @param:JsonProperty("created_at") val createdAt: String,
        )
        
        val rs = Response(
            requireNotNull(report.id),
            report.vehicleNumber,
            report.violationType,
            report.violationTime.toString(),
            report.violationLocation,
            report.violationContent,
            report.evidenceInfo,
            report.status.name,
            report.handlingRemark,
            report.handledAt?.toString(),
            report.createdAt.toString(),
        )
        return responseBuilder.ok().data(rs).build()
    }
    
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @PreAuthorize("isAuthenticated()")
    @Transactional
    fun createReport(
        @AuthenticationPrincipal principal: CurrentUserPrincipal,
        @RequestParam(name = "vehicle_number") vehicleNumber: String,
        @RequestParam(name = "violation_type") violationType: String,
        @RequestParam(name = "violation_time") violationTime: LocalDateTime,
        @RequestParam(required = false) location: String?,
        @RequestParam description: String,
        @RequestPart(name = "evidence", required = false) evidence: MultipartFile?,
    ): ResponseEntity<Response> {
        require(vehicleNumber.isNotBlank()) { "请填写车牌号码" }
        require(violationType.isNotBlank()) { "请选择或填写违规类型" }
        require(description.isNotBlank()) { "请填写违规说明" }
        require(vehicleNumber.trim().length <= 64) { "车牌号码不能超过 64 个字符" }
        require(violationType.trim().length <= 64) { "违规类型不能超过 64 个字符" }
        require(description.trim().length <= 1024) { "违规说明不能超过 1024 个字符" }
        require(location.isNullOrBlank() || location.trim().length <= 255) { "发生地点不能超过 255 个字符" }
        
        val evidenceInfo = evidence?.takeUnless { it.isEmpty }?.let { file ->
            require(file.size <= EVIDENCE_IMAGE_MAX_BYTES) { "证据图片不能超过 5MB" }
            require(file.contentType?.startsWith("image/", ignoreCase = true) == true) { "证据图片必须为图片格式" }
            val extension = file.originalFilename?.substringAfterLast('.', "")?.lowercase(Locale.ROOT)
            require(extension in EVIDENCE_IMAGE_EXTENSIONS) { "证据图片格式不受支持" }
            val header = ByteArray(IMAGE_HEADER_BYTES)
            val size = file.inputStream.use { it.read(header) }
            fun matches(offset: Int, vararg expected: Int): Boolean =
                size >= offset + expected.size && expected.indices.all { header[offset + it] == expected[it].toByte() }
            
            val validImage = size >= 4 && (
                    matches(0, 0xFF, 0xD8, 0xFF) ||
                            matches(0, 0x89, 'P'.code, 'N'.code, 'G'.code) ||
                            matches(0, 'G'.code, 'I'.code, 'F'.code) ||
                            matches(0, 'B'.code, 'M'.code) ||
                            (matches(0, 'R'.code, 'I'.code, 'F'.code, 'F'.code) &&
                                    matches(8, 'W'.code, 'E'.code, 'B'.code, 'P'.code))
                    )
            require(validImage) { "证据图片内容不是受支持的图片" }
            fileService.upload(file).downloadUrl
        }
        
        val report = violationReportRepository.save(
            ViolationReport().apply {
                reporterUserId = principal.userId
                this.vehicleNumber = vehicleNumber.trim().uppercase(Locale.ROOT)
                this.violationType = violationType.trim()
                this.violationTime = violationTime
                violationLocation = location?.trim()?.takeIf(String::isNotEmpty)
                violationContent = description.trim()
                this.evidenceInfo = evidenceInfo
            },
        )
        
        data class Response(val id: Long, val status: String)
        
        val rs = Response(requireNotNull(report.id), report.status.name)
        return responseBuilder.created().message("举报已提交").data(rs).build()
    }
    
    private companion object {
        const val EVIDENCE_IMAGE_MAX_BYTES = 5 * 1024 * 1024
        const val IMAGE_HEADER_BYTES = 12
        val EVIDENCE_IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
    }
}
