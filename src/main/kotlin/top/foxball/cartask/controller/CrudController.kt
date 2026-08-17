package top.foxball.cartask.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import top.foxball.cartask.service.CrudService
import top.foxball.cartask.shared.Response
import top.foxball.cartask.shared.ResponseBuilder

/** 为简单业务实体提供统一的单条和批量 CRUD 端点。 */
abstract class CrudController<T : Any>(
    private val service: CrudService<T>,
    private val responseBuilder: ResponseBuilder,
) {
    @PostMapping
    fun create(@RequestBody entity: T): ResponseEntity<Response> =
        responseBuilder.created().data(service.create(entity)).build()

    @PostMapping("/batch")
    fun createBatch(@RequestBody entities: List<T>): ResponseEntity<Response> =
        responseBuilder.created().data(service.createBatch(entities)).build()

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): ResponseEntity<Response> =
        responseBuilder.ok().data(service.get(id)).build()

    @GetMapping("/batch")
    fun getBatch(@RequestParam id: List<Long>): ResponseEntity<Response> =
        responseBuilder.ok().data(service.getBatch(id)).build()

    @GetMapping
    fun list(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(name = "page_size", defaultValue = "20") pageSize: Int,
    ): ResponseEntity<Response> = responseBuilder.ok().data(service.list(page, pageSize)).build()

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @RequestBody entity: T): ResponseEntity<Response> =
        responseBuilder.ok().data(service.update(id, entity)).build()

    @PutMapping("/batch")
    fun updateBatch(@RequestBody entities: List<T>): ResponseEntity<Response> =
        responseBuilder.ok().data(service.updateBatch(entities)).build()

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long): ResponseEntity<Response> {
        service.delete(id)
        return responseBuilder.ok().data(mapOf("id" to id)).build()
    }

    @DeleteMapping("/batch")
    fun deleteBatch(@RequestParam id: List<Long>): ResponseEntity<Response> {
        service.deleteBatch(id)
        return responseBuilder.ok().data(mapOf("ids" to id)).build()
    }
}
