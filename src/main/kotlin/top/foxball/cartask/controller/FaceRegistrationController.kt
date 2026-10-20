package top.foxball.cartask.controller

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.FileSystemResource
import org.springframework.http.*
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import top.foxball.cartask.entity.GatePerson
import top.foxball.cartask.service.FaceRegistrationService
import top.foxball.cartask.service.FaceRegistrationWorkbookService
import top.foxball.cartask.shared.Response
import top.foxball.cartask.shared.ResponseBuilder
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/face-registrations")
class FaceRegistrationController(
    private val faceRegistrationService: FaceRegistrationService,
    private val responseBuilder: ResponseBuilder,
    private val workbookService: FaceRegistrationWorkbookService,
) {
    @GetMapping
    @PreAuthorize("hasAuthority('gate-person:read')")
    fun list(
        @RequestParam(required = false) keyword: String?,
        @RequestParam(name = "department_code", required = false) departmentCode: String?,
        @RequestParam(name = "approve_status", required = false) approveStatus: String?,
        @RequestParam(name = "sync_status", required = false) syncStatus: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(name = "page_size", defaultValue = "20") pageSize: Int,
    ): ResponseEntity<Response> {
        data class ItemData(
            val id: Long,
            val code: String,
            val name: String,
            val phone: String,
            @param:JsonProperty("id_card") val idCard: String,
            val dept: String,
            @param:JsonProperty("department_code") val departmentCode: String,
            @param:JsonProperty("photo_url") val photoUrl: String?,
            @param:JsonProperty("approve_status") val approveStatus: String,
            @param:JsonProperty("sync_status") val syncStatus: String,
            @param:JsonProperty("create_time") val createTime: LocalDateTime,
            @param:JsonProperty("updated_at") val updatedAt: LocalDateTime,
        )
        data class Response(val items: List<ItemData>, val total: Int)
        val sensitive = SecurityContextHolder.getContext().authentication?.authorities
            ?.any { it.authority == "gate-person:sensitive-read" } == true
        val parsedApprove = approveStatus?.takeIf { it.isNotBlank() }?.let {
            GatePerson.ApproveStatus.entries.firstOrNull { status -> status.name == it || status.value() == it }
                ?: throw IllegalArgumentException("审核状态不正确")
        }
        val parsedSync = syncStatus?.takeIf { it.isNotBlank() }?.let {
            GatePerson.SyncStatus.entries.firstOrNull { status -> status.name == it || status.value() == it }
                ?: throw IllegalArgumentException("同步状态不正确")
        }
        val result = faceRegistrationService.list(
            FaceRegistrationService.ListQuery(keyword, departmentCode, parsedApprove, parsedSync), page, pageSize,
        )
        val rs = Response(result.items.map {
            ItemData(
                it.id, it.code, it.name, it.phone,
                if (sensitive) it.idCard else "************${it.idCard.takeLast(4)}",
                it.dept, it.departmentCode, it.photoUrl, it.approveStatus.value(), it.syncStatus.value(),
                it.createTime, it.updatedAt,
            )
        }, result.total)
        return responseBuilder.ok().data(rs).build()
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('gate-person:read')")
    fun get(@PathVariable id: Long): ResponseEntity<Response> {
        data class Response(
            val id: Long,
            val code: String,
            val name: String,
            val phone: String,
            @param:JsonProperty("id_card") val idCard: String,
            val dept: String,
            @param:JsonProperty("department_code") val departmentCode: String,
            @param:JsonProperty("photo_url") val photoUrl: String?,
            @param:JsonProperty("approve_status") val approveStatus: String,
            @param:JsonProperty("sync_status") val syncStatus: String,
            @param:JsonProperty("create_time") val createTime: LocalDateTime,
            @param:JsonProperty("updated_at") val updatedAt: LocalDateTime,
        )
        val item = faceRegistrationService.get(id)
        val sensitive = SecurityContextHolder.getContext().authentication?.authorities
            ?.any { it.authority == "gate-person:sensitive-read" } == true
        val rs = Response(
            item.id, item.code, item.name, item.phone,
            if (sensitive) item.idCard else "************${item.idCard.takeLast(4)}",
            item.dept, item.departmentCode, item.photoUrl, item.approveStatus.value(), item.syncStatus.value(),
            item.createTime, item.updatedAt,
        )
        return responseBuilder.ok().data(rs).build()
    }

    @GetMapping("/{id}/photo")
    @PreAuthorize("hasAuthority('gate-person:read')")
    fun photo(@PathVariable id: Long): ResponseEntity<FileSystemResource> {
        val file = faceRegistrationService.photo(id)
        val mediaType = try {
            file.contentType?.let(MediaType::parseMediaType) ?: MediaType.APPLICATION_OCTET_STREAM
        } catch (_: InvalidMediaTypeException) {
            MediaType.APPLICATION_OCTET_STREAM
        }
        return ResponseEntity.ok()
            .contentType(mediaType)
            .contentLength(file.sizeBytes)
            .cacheControl(CacheControl.noStore())
            .header("X-Content-Type-Options", "nosniff")
            .body(FileSystemResource(file.path))
    }

    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @PreAuthorize("hasAuthority('gate-person:manage')")
    fun register(
        @RequestPart("name") name: String,
        @RequestPart("phone") phone: String,
        @RequestPart("id_card") idCard: String,
        @RequestPart("photo") photo: MultipartFile,
        @RequestPart(name = "department_code", required = false) departmentCode: String?,
    ): ResponseEntity<Response> {
        data class Response(
            val id: Long,
            val code: String,
            @param:JsonProperty("approve_status") val approveStatus: String,
            @param:JsonProperty("sync_status") val syncStatus: String,
        )
        val item = faceRegistrationService.register(name, phone, idCard, photo, departmentCode)
        val rs = Response(item.id, item.code, item.approveStatus.value(), item.syncStatus.value())
        return responseBuilder.created().message("人脸登记已提交审核").data(rs).build()
    }

    @PutMapping("/{id}", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @PreAuthorize("hasAuthority('gate-person:manage')")
    fun update(
        @PathVariable id: Long,
        @RequestPart(name = "name", required = false) name: String?,
        @RequestPart(name = "phone", required = false) phone: String?,
        @RequestPart(name = "id_card", required = false) idCard: String?,
        @RequestPart(name = "photo", required = false) photo: MultipartFile?,
    ): ResponseEntity<Response> {
        data class Response(
            val id: Long,
            val code: String,
            @param:JsonProperty("approve_status") val approveStatus: String,
            @param:JsonProperty("sync_status") val syncStatus: String,
        )
        val item = faceRegistrationService.update(id, name, phone, idCard, photo)
        val rs = Response(item.id, item.code, item.approveStatus.value(), item.syncStatus.value())
        return responseBuilder.ok().message("人脸登记已更新").data(rs).build()
    }

    @PutMapping("/{id}/review")
    @PreAuthorize("hasAuthority('gate-person:review')")
    fun review(
        @PathVariable id: Long,
        @RequestParam approved: Boolean,
        @RequestParam(required = false) reason: String?,
    ): ResponseEntity<Response> {
        data class Response(
            val id: Long,
            val code: String,
            @param:JsonProperty("approve_status") val approveStatus: String,
            @param:JsonProperty("sync_status") val syncStatus: String,
        )
        val item = faceRegistrationService.review(id, approved, reason)
        val rs = Response(item.id, item.code, item.approveStatus.value(), item.syncStatus.value())
        return responseBuilder.ok().message(if (approved) "审核通过" else "审核已拒绝").data(rs).build()
    }

    @PutMapping("/reviews")
    @PreAuthorize("hasAuthority('gate-person:review')")
    fun reviewBatch(
        @RequestParam ids: List<Long>,
        @RequestParam approved: Boolean,
        @RequestParam(required = false) reason: String?,
    ): ResponseEntity<Response> {
        data class FailureData(val id: Long, val reason: String)
        data class BatchResponse(val reviewed: Int, val failures: List<FailureData>)
        val result = faceRegistrationService.reviewBatch(ids, approved, reason)
        val rs = BatchResponse(result.reviewed, result.failures.map { FailureData(it.id, it.reason) })
        return responseBuilder.ok().message("已审核 ${result.reviewed} 人").data(rs).build()
    }

    @GetMapping("/template")
    @PreAuthorize("hasAuthority('gate-person:read')")
    fun template(): ResponseEntity<ByteArrayResource> {
        val output = ByteArrayOutputStream()
        org.apache.poi.xssf.usermodel.XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("人脸登记")
            val header = sheet.createRow(0)
            listOf("姓名", "手机号", "身份证号", "照片文件名", "单位编码").forEachIndexed { index, value ->
                header.createCell(index).setCellValue(value)
            }
            workbook.write(output)
        }
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("人脸登记导入模板.xlsx", StandardCharsets.UTF_8).build().toString())
            .header("X-Content-Type-Options", "nosniff")
            .body(ByteArrayResource(output.toByteArray()))
    }

    @PostMapping("/import", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @PreAuthorize("hasAuthority('gate-person:manage')")
    fun import(
        @RequestPart("file") file: MultipartFile,
        @RequestPart("photos") photos: MultipartFile,
        @RequestParam(defaultValue = "false") preview: Boolean,
    ): ResponseEntity<Response> {
        data class RowData(
            val row: Int,
            val name: String,
            @param:JsonProperty("id_card_last_four") val idCardLastFour: String,
            @param:JsonProperty("photo_filename") val photoFilename: String,
            val errors: List<String>,
        )
        data class ImportResponse(val imported: Int, val valid: Boolean, val rows: List<RowData>)
        val result = workbookService.import(file, photos, preview)
        val rs = ImportResponse(result.imported, result.valid, result.rows.map {
            RowData(it.row, it.name, it.idCardLastFour, it.photoFilename, it.errors)
        })
        return responseBuilder.ok().message(
            if (!result.valid) "导入校验未通过" else if (preview) "导入预览完成" else "导入完成",
        ).data(rs).build()
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('gate-person:export')")
    fun export(
        @RequestParam(required = false) keyword: String?,
        @RequestParam(name = "department_code", required = false) departmentCode: String?,
        @RequestParam(name = "approve_status", required = false) approveStatus: String?,
        @RequestParam(name = "sync_status", required = false) syncStatus: String?,
    ): ResponseEntity<ByteArrayResource> {
        val parsedApprove = approveStatus?.takeIf(String::isNotBlank)?.let {
            GatePerson.ApproveStatus.entries.firstOrNull { status -> status.name == it || status.value() == it }
                ?: throw IllegalArgumentException("审核状态不正确")
        }
        val parsedSync = syncStatus?.takeIf(String::isNotBlank)?.let {
            GatePerson.SyncStatus.entries.firstOrNull { status -> status.name == it || status.value() == it }
                ?: throw IllegalArgumentException("同步状态不正确")
        }
        val bytes = workbookService.export(FaceRegistrationService.ListQuery(keyword, departmentCode, parsedApprove, parsedSync))
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("人脸登记导出.xlsx", StandardCharsets.UTF_8).build().toString())
            .header("X-Content-Type-Options", "nosniff")
            .body(ByteArrayResource(bytes))
    }
}
