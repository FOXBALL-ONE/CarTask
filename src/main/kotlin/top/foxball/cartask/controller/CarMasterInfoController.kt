package top.foxball.cartask.controller

import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import top.foxball.cartask.entity.CarMasterInfo
import top.foxball.cartask.service.CarMasterInfoService
import top.foxball.cartask.shared.Response
import top.foxball.cartask.shared.ResponseBuilder

@RestController
@RequestMapping("/api/car-master-infos")
@PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")

/** class CarMasterInfoController：Web 控制器，负责接收 HTTP 请求、调用领域服务并构造统一响应。 */
class CarMasterInfoController(
    private val service: CarMasterInfoService,
    private val responseBuilder: ResponseBuilder,
) {
    
    @PostMapping
    @PreAuthorize("hasAuthority('vehicle:create')")
            /** create：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun create(@RequestBody entity: CarMasterInfo): ResponseEntity<Response> =
        responseBuilder.created().data(service.create(entity)).build()
    
    
    @PostMapping("/batch")
    @PreAuthorize("hasAuthority('vehicle:create')")
            /** createBatch：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun createBatch(@RequestBody entities: List<CarMasterInfo>): ResponseEntity<Response> =
        responseBuilder.created().data(service.createBatch(entities)).build()
    
    
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('vehicle:read')")
            /** get：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun get(@PathVariable id: Long): ResponseEntity<Response> =
        responseBuilder.ok().data(service.get(id)).build()
    
    
    @GetMapping("/batch")
    @PreAuthorize("hasAuthority('vehicle:read')")
            /** getBatch：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun getBatch(@RequestParam id: List<Long>): ResponseEntity<Response> =
        responseBuilder.ok().data(service.getBatch(id)).build()
    
    
    @GetMapping
    @PreAuthorize("hasAuthority('vehicle:read')")
            /** list：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun list(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(name = "page_size", defaultValue = "20") pageSize: Int,
    ): ResponseEntity<Response> = responseBuilder.ok().data(service.list(page, pageSize)).build()
    
    
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('vehicle:update')")
            /** update：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun update(@PathVariable id: Long, @RequestBody entity: CarMasterInfo): ResponseEntity<Response> =
        responseBuilder.ok().data(service.update(id, entity)).build()
    
    
    @PutMapping("/batch")
    @PreAuthorize("hasAuthority('vehicle:update')")
            /** updateBatch：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun updateBatch(@RequestBody entities: List<CarMasterInfo>): ResponseEntity<Response> =
        responseBuilder.ok().data(service.updateBatch(entities)).build()
    
    
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('vehicle:delete')")
            /** delete：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun delete(@PathVariable id: Long): ResponseEntity<Response> {
        service.delete(id)
        return responseBuilder.ok().data(mapOf("id" to id)).build()
    }
    
    
    @DeleteMapping("/batch")
    @PreAuthorize("hasAuthority('vehicle:delete')")
            /** deleteBatch：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun deleteBatch(@RequestParam id: List<Long>): ResponseEntity<Response> {
        service.deleteBatch(id)
        return responseBuilder.ok().data(mapOf("ids" to id)).build()
    }
}
