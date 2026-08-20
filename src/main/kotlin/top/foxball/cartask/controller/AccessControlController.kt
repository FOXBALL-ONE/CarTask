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
import top.foxball.cartask.entity.AccessControl
import top.foxball.cartask.service.AccessControlService
import top.foxball.cartask.shared.Response
import top.foxball.cartask.shared.ResponseBuilder

@RestController
@RequestMapping("/api/access-controls")
/** 门禁授权记录的管理接口。 */
class AccessControlController(
    private val service: AccessControlService,
    private val responseBuilder: ResponseBuilder,
) {
    /** 创建一条实体记录。 */
    @PostMapping
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN')) and hasAuthority('access-control:apply')")
    fun create(@RequestBody entity: AccessControl): ResponseEntity<Response> =
        responseBuilder.created().data(service.create(entity)).build()

    /** 批量创建实体记录。 */
    @PostMapping("/batch")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN')) and hasAuthority('access-control:apply')")
    fun createBatch(@RequestBody entities: List<AccessControl>): ResponseEntity<Response> =
        responseBuilder.created().data(service.createBatch(entities)).build()

    /** 按主键获取一条实体记录。 */
    @GetMapping("/{id}")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN')) and hasAuthority('access-control:read')")
    fun get(@PathVariable id: Long): ResponseEntity<Response> =
        responseBuilder.ok().data(service.get(id)).build()

    /** 按多个主键批量获取实体记录。 */
    @GetMapping("/batch")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN')) and hasAuthority('access-control:read')")
    fun getBatch(@RequestParam id: List<Long>): ResponseEntity<Response> =
        responseBuilder.ok().data(service.getBatch(id)).build()

    /** 分页查询实体记录。 */
    @GetMapping
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN')) and hasAuthority('access-control:read')")
    fun list(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(name = "page_size", defaultValue = "20") pageSize: Int,
    ): ResponseEntity<Response> = responseBuilder.ok().data(service.list(page, pageSize)).build()

    /** 更新指定主键的实体记录。 */
    @PutMapping("/{id}")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN')) and hasAuthority('access-control:update')")
    fun update(@PathVariable id: Long, @RequestBody entity: AccessControl): ResponseEntity<Response> =
        responseBuilder.ok().data(service.update(id, entity)).build()

    /** 批量更新实体记录。 */
    @PutMapping("/batch")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN')) and hasAuthority('access-control:update')")
    fun updateBatch(@RequestBody entities: List<AccessControl>): ResponseEntity<Response> =
        responseBuilder.ok().data(service.updateBatch(entities)).build()

    @PostMapping("/{id}/review")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN')) and hasAuthority('access-control:review')")
    fun review(
        @PathVariable id: Long,
        @RequestParam approved: Boolean,
        @RequestParam(name = "review_reason") reason: String,
    ): ResponseEntity<Response> = responseBuilder.ok().data(service.review(id, approved, reason)).build()

    @PostMapping("/{id}/sync")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN')) and hasAuthority('access-control:sync')")
    fun synchronize(@PathVariable id: Long): ResponseEntity<Response> =
        responseBuilder.ok().data(service.synchronize(id)).build()

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
