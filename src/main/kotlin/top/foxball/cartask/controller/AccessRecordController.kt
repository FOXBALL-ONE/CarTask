package top.foxball.cartask.controller

import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import top.foxball.cartask.entity.AccessRecord
import top.foxball.cartask.service.AccessRecordService
import top.foxball.cartask.shared.Response
import top.foxball.cartask.shared.ResponseBuilder

@RestController
@RequestMapping("/api/access-records")
/** 车辆进出记录的查询与管理接口。 */
class AccessRecordController(
    private val service: AccessRecordService,
    private val responseBuilder: ResponseBuilder,
) {
    /** 创建一条实体记录。 */
    @PostMapping
    @PreAuthorize("denyAll()")
    fun create(@RequestBody entity: AccessRecord): ResponseEntity<Response> =
        responseBuilder.created().data(service.create(entity)).build()

    /** 批量创建实体记录。 */
    @PostMapping("/batch")
    @PreAuthorize("denyAll()")
    fun createBatch(@RequestBody entities: List<AccessRecord>): ResponseEntity<Response> =
        responseBuilder.created().data(service.createBatch(entities)).build()

    /** 按主键获取一条实体记录。 */
    @GetMapping("/{id}")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN')) and hasAuthority('access-record:read')")
    fun get(@PathVariable id: Long): ResponseEntity<Response> =
        responseBuilder.ok().data(service.get(id)).build()

    /** 按多个主键批量获取实体记录。 */
    @GetMapping("/batch")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN')) and hasAuthority('access-record:read')")
    fun getBatch(@RequestParam id: List<Long>): ResponseEntity<Response> =
        responseBuilder.ok().data(service.getBatch(id)).build()

    /** 分页查询实体记录。 */
    @GetMapping
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN')) and hasAuthority('access-record:read')")
    fun list(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(name = "page_size", defaultValue = "20") pageSize: Int,
    ): ResponseEntity<Response> = responseBuilder.ok().data(service.list(page, pageSize)).build()

    /** 更新指定主键的实体记录。 */
    @PutMapping("/{id}")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN')) and hasAuthority('access-record:correct')")
    fun update(
        @PathVariable id: Long,
        @RequestParam(name = "correction_reason") correctionReason: String,
        @RequestBody entity: AccessRecord,
    ): ResponseEntity<Response> = responseBuilder.ok().data(service.correct(id, entity, correctionReason)).build()

    /** 批量更新实体记录。 */
    @PutMapping("/batch")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN')) and hasAuthority('access-record:correct')")
    fun updateBatch(
        @RequestParam(name = "correction_reason") correctionReason: String,
        @RequestBody entities: List<AccessRecord>,
    ): ResponseEntity<Response> = responseBuilder.ok().data(service.correctBatch(entities, correctionReason)).build()

    @PostMapping("/{id}/release")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN')) and hasAuthority('access-record:release')")
    fun release(
        @PathVariable id: Long,
        @RequestParam(name = "release_reason") releaseReason: String,
    ): ResponseEntity<Response> = responseBuilder.ok().data(service.release(id, releaseReason)).build()

    /** 删除指定主键的实体记录。 */
    @DeleteMapping("/{id}")
    @PreAuthorize("denyAll()")
    fun delete(@PathVariable id: Long): ResponseEntity<Response> {
        service.delete(id)
        return responseBuilder.ok().data(mapOf("id" to id)).build()
    }

    /** 批量删除实体记录。 */
    @DeleteMapping("/batch")
    @PreAuthorize("denyAll()")
    fun deleteBatch(@RequestParam id: List<Long>): ResponseEntity<Response> {
        service.deleteBatch(id)
        return responseBuilder.ok().data(mapOf("ids" to id)).build()
    }
}
