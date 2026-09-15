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

/**
 * 门禁授权的对外视图。
 *
 * 刻意不直接序列化 [AccessControl] 实体：`department` 是懒加载关联，而请求体里传进来的
 * 往往只是一个 `{id: 1}` 的空壳，直接回写响应会在 `Department` 那些 lateinit 字段上抛
 * `UninitializedPropertyAccessException`——接口报 500，记录却已经落库了。
 * 这里只取外键 id 与在事务内初始化好的部门名，与实体代理状态无关。
 */
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
        /** lateinit 属性还没被赋值时按 null 取，而不是抛 [UninitializedPropertyAccessException]。 */
        private inline fun <T> uninitializedAsNull(get: () -> T): T? = try {
            get()
        } catch (_: UninitializedPropertyAccessException) {
            null
        }

        /** 只读两个关联的 `id`（外键列，代理无需初始化）与已在事务内取好的名称。 */
        fun of(entity: AccessControl): AccessControlView = AccessControlView(
            id = entity.id,
            name = entity.name,
            phone = entity.phone,
            personNumber = entity.personNumber,
            faceInfo = entity.faceInfo,
            accessControlList = entity.accessControlList,
            accessControlPermissionId = entity.accessControlPermission?.id,
            accessControlPermissionName = entity.accessControlPermission
                ?.let { permission -> uninitializedAsNull { permission.accessControlName } },
            departmentId = entity.department?.id,
            departmentName = entity.department?.name,
            upTime = entity.upTime,
            endTime = entity.endTime,
            reviewStatus = entity.reviewStatus.name,
            synchronizedLoading = entity.synchronizedLoading,
            // createdAt / updatedAt 由 AuditingEntityListener 在持久化时填，非自增主键场景下
            // 视图可能在赋值前就被构造出来，这里按 null 处理。
            createdAt = uninitializedAsNull { entity.createdAt },
            updatedAt = uninitializedAsNull { entity.updatedAt },
        )
    }
}

private fun AccessControl.toView(): AccessControlView = AccessControlView.of(this)

private fun Page<AccessControl>.toViews(): Page<AccessControlView> = map { it.toView() }

@RestController
@RequestMapping("/api/access-controls")
/** 门禁授权记录的管理接口。 */
class AccessControlController(
    private val service: AccessControlService,
    private val responseBuilder: ResponseBuilder,
) {
    /** 创建一条实体记录。 */
    @PostMapping
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('access-control:apply')")
    fun create(@RequestBody entity: AccessControl): ResponseEntity<Response> =
        responseBuilder.created().data(service.create(entity).toView()).build()

    /** 批量创建实体记录。 */
    @PostMapping("/batch")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('access-control:apply')")
    fun createBatch(@RequestBody entities: List<AccessControl>): ResponseEntity<Response> =
        responseBuilder.created().data(service.createBatch(entities).map { it.toView() }).build()

    /** 按主键获取一条实体记录。 */
    @GetMapping("/{id}")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('access-control:read')")
    fun get(@PathVariable id: Long): ResponseEntity<Response> =
        responseBuilder.ok().data(service.get(id).toView()).build()

    /** 按多个主键批量获取实体记录。 */
    @GetMapping("/batch")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('access-control:read')")
    fun getBatch(@RequestParam id: List<Long>): ResponseEntity<Response> =
        responseBuilder.ok().data(service.getBatch(id).map { it.toView() }).build()

    /** 分页查询实体记录；结果按当前数据范围裁剪。 */
    @GetMapping
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('access-control:read')")
    fun list(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(name = "page_size", defaultValue = "20") pageSize: Int,
    ): ResponseEntity<Response> = responseBuilder.ok().data(service.list(page, pageSize).toViews()).build()

    /** 更新指定主键的实体记录。 */
    @PutMapping("/{id}")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('access-control:update')")
    fun update(@PathVariable id: Long, @RequestBody entity: AccessControl): ResponseEntity<Response> =
        responseBuilder.ok().data(service.update(id, entity).toView()).build()

    /** 批量更新实体记录。 */
    @PutMapping("/batch")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('access-control:update')")
    fun updateBatch(@RequestBody entities: List<AccessControl>): ResponseEntity<Response> =
        responseBuilder.ok().data(service.updateBatch(entities).map { it.toView() }).build()

    @PostMapping("/{id}/review")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('access-control:review')")
            /**
             * review：执行当前模块中的业务操作。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @param id 参与本次处理的输入参数。
             * @param approved 参与本次处理的输入参数。
             * @param reason 参与本次处理的输入参数。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun review(
        @PathVariable id: Long,
        @RequestParam approved: Boolean,
        @RequestParam(name = "review_reason") reason: String,
    ): ResponseEntity<Response> =
        responseBuilder.ok().data(service.review(id, approved, reason).toView()).build()

    @PostMapping("/{id}/sync")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('access-control:sync')")
    fun synchronize(@PathVariable id: Long): ResponseEntity<Response> =
        responseBuilder.ok().data(service.synchronize(id).toView()).build()

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
