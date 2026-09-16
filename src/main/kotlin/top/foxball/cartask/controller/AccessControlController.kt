package top.foxball.cartask.controller

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.data.domain.Page
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import top.foxball.cartask.entity.AccessControl
import top.foxball.cartask.service.AccessControlService
import top.foxball.cartask.shared.Response
import top.foxball.cartask.shared.ResponseBuilder
import java.time.LocalDateTime


data class AccessControlView(
    val id: Long?,
    val name: String,
    val phone: String?,
    @param:JsonProperty("personNumber") val personNumber: String?,
    
    @param:JsonProperty("faceInfo") val faceInfo: String?,
    
    @param:JsonProperty("accessControlList") val accessControlList: String?,
    
    @param:JsonProperty("accessControlPermissionId") val accessControlPermissionId: Long?,
    
    @param:JsonProperty("accessControlPermissionName") val accessControlPermissionName: String?,
    
    @param:JsonProperty("departmentId") val departmentId: Long?,
    
    @param:JsonProperty("departmentName") val departmentName: String?,
    
    @param:JsonProperty("upTime") val upTime: LocalDateTime?,
    
    @param:JsonProperty("endTime") val endTime: LocalDateTime?,
    
    @param:JsonProperty("reviewStatus") val reviewStatus: String,
    
    @param:JsonProperty("synchronizedLoading") val synchronizedLoading: Boolean,
    
    @param:JsonProperty("createdAt") val createdAt: LocalDateTime?,
    
    @param:JsonProperty("updatedAt") val updatedAt: LocalDateTime?,
) {
    companion object {
        
        private inline fun <T> uninitializedAsNull(get: () -> T): T? = try {
            get()
        } catch (_: UninitializedPropertyAccessException) {
            null
        }
        
        
        /** of：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
        fun of(entity: AccessControl): AccessControlView = AccessControlView(
            id = entity.id,
            name = entity.name,
            phone = entity.phone,
            personNumber = entity.personNumber,
            faceInfo = entity.faceInfo,
            accessControlList = entity.accessControlList,
            accessControlPermissionId = entity.accessControlPermission?.id,
            accessControlPermissionName = entity.accessControlPermission?.let {
                permission -> uninitializedAsNull { permission.accessControlName }
            },
            departmentId = entity.department?.id,
            departmentName = entity.department?.name,
            upTime = entity.upTime,
            endTime = entity.endTime,
            reviewStatus = entity.reviewStatus.name,
            synchronizedLoading = entity.synchronizedLoading,
            createdAt = uninitializedAsNull { entity.createdAt },
            updatedAt = uninitializedAsNull { entity.updatedAt },
        )
    }
}

private fun AccessControl.toView(): AccessControlView = AccessControlView.of(this)

private fun Page<AccessControl>.toViews(): Page<AccessControlView> = map { it.toView() }

@RestController
@RequestMapping("/api/access-controls")

/** class AccessControlController：Web 控制器，负责接收 HTTP 请求、调用领域服务并构造统一响应。 */
class AccessControlController(
    private val service: AccessControlService,
    private val responseBuilder: ResponseBuilder,
) {
    
    @PostMapping
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('access-control:apply')")
            /** create：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun create(@RequestBody entity: AccessControl): ResponseEntity<Response> =
        responseBuilder.created().data(service.create(entity).toView()).build()
    
    
    @PostMapping("/batch")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('access-control:apply')")
            /** createBatch：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun createBatch(@RequestBody entities: List<AccessControl>): ResponseEntity<Response> =
        responseBuilder.created().data(service.createBatch(entities).map { it.toView() }).build()
    
    
    @GetMapping("/{id}")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('access-control:read')")
            /** get：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun get(@PathVariable id: Long): ResponseEntity<Response> =
        responseBuilder.ok().data(service.get(id).toView()).build()
    
    
    @GetMapping("/batch")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('access-control:read')")
            /** getBatch：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun getBatch(@RequestParam id: List<Long>): ResponseEntity<Response> =
        responseBuilder.ok().data(service.getBatch(id).map { it.toView() }).build()
    
    
    @GetMapping
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('access-control:read')")
            /** list：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun list(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(name = "page_size", defaultValue = "20") pageSize: Int,
    ): ResponseEntity<Response> = responseBuilder.ok().data(service.list(page, pageSize).toViews()).build()
    
    
    @PutMapping("/{id}")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('access-control:update')")
            /** update：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun update(@PathVariable id: Long, @RequestBody entity: AccessControl): ResponseEntity<Response> =
        responseBuilder.ok().data(service.update(id, entity).toView()).build()
    
    
    @PutMapping("/batch")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('access-control:update')")
            /** updateBatch：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun updateBatch(@RequestBody entities: List<AccessControl>): ResponseEntity<Response> =
        responseBuilder.ok().data(service.updateBatch(entities).map { it.toView() }).build()
    
    @PostMapping("/{id}/review")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('access-control:review')")
            
            
            /** review：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun review(
        @PathVariable id: Long,
        @RequestParam approved: Boolean,
        @RequestParam(name = "review_reason") reason: String,
    ): ResponseEntity<Response> = responseBuilder.ok().data(service.review(id, approved, reason).toView()).build()
    
    @PostMapping("/{id}/sync")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('access-control:sync')")
            /** synchronize：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun synchronize(@PathVariable id: Long): ResponseEntity<Response> =
        responseBuilder.ok().data(service.synchronize(id).toView()).build()
    
    
    @DeleteMapping("/{id}")
    @PreAuthorize("denyAll()")
            /** delete：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun delete(@PathVariable id: Long): ResponseEntity<Response> {
        service.delete(id)
        return responseBuilder.ok().data(mapOf("id" to id)).build()
    }
    
    
    @DeleteMapping("/batch")
    @PreAuthorize("denyAll()")
            /** deleteBatch：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun deleteBatch(@RequestParam id: List<Long>): ResponseEntity<Response> {
        service.deleteBatch(id)
        return responseBuilder.ok().data(mapOf("ids" to id)).build()
    }
}
