package top.foxball.cartask.controller

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
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
import top.foxball.cartask.entity.Role
import top.foxball.cartask.service.RoleService
import top.foxball.cartask.shared.Response
import top.foxball.cartask.shared.ResponseBuilder

@RestController
@RequestMapping("/api/roles")
/** 角色及其权限集合的管理接口。 */
class RoleController(
    private val service: RoleService,
    private val responseBuilder: ResponseBuilder,
) {
    private val documentRoleSequence = AtomicLong(1_000_000)
    private val documentRoles = ConcurrentHashMap<Long, DocumentRoleRequest>()

    /** 创建文档约定的业务角色。 */
    @PostMapping(consumes = ["application/json"])
    @PreAuthorize("hasRole('SUPER_ADMIN') and hasAuthority('role:manage')")
    fun create(@RequestBody body: DocumentRoleRequest): ResponseEntity<Response> {
        data class Response(
            val id: Long,
            val name: String,
            val code: String,
            val sort: Int,
            val status: Int,
            val remark: String?,
        )

        val name = requireNotNull(body.name) { "角色名称不能为空" }
        val code = requireNotNull(body.code) { "角色编码不能为空" }
        require(documentRoles.values.none { it.code.equals(code, true) }) { "角色编码已存在" }
        val id = documentRoleSequence.getAndIncrement()
        val role = DocumentRoleRequest(name, code, body.sort ?: 0, body.status ?: 1, body.remark)
        documentRoles[id] = role
        val rs = Response(id, name, code, role.sort ?: 0, role.status ?: 1, role.remark)
        return responseBuilder.created().data(rs).build()
    }

    /** 批量创建实体记录。 */
    @PostMapping("/batch")
    @PreAuthorize("hasRole('SUPER_ADMIN') and hasAuthority('role:manage')")
    fun createBatch(@RequestBody entities: List<Role>): ResponseEntity<Response> =
        responseBuilder.created().data(service.createBatch(entities)).build()

    /** 按主键获取一条实体记录。 */
    @GetMapping("/{id}")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN')) and hasAuthority('role:read')")
    fun get(@PathVariable id: Long): ResponseEntity<Response> =
        responseBuilder.ok().data(service.get(id)).build()

    /** 按多个主键批量获取实体记录。 */
    @GetMapping("/batch")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN')) and hasAuthority('role:read')")
    fun getBatch(@RequestParam id: List<Long>): ResponseEntity<Response> =
        responseBuilder.ok().data(service.getBatch(id)).build()

    /** 返回文档约定的角色列表。 */
    @GetMapping
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN')) and hasAuthority('role:read')")
    fun list(): ResponseEntity<Response> {
        data class RoleData(
            val id: Long,
            val name: String,
            val code: String,
            val sort: Int,
            val status: Int,
            val remark: String?,
        )

        val systemRoles = service.list(1, 100).content.map {
            RoleData(
                requireNotNull(it.id), it.description ?: it.name, it.documentCode ?: it.name,
                it.documentSort ?: 0, it.documentStatus ?: if (it.enabled) 1 else 0, it.documentRemark,
            )
        }
        val rs = systemRoles + documentRoles.entries.sortedBy { it.key }.map { (id, role) ->
            RoleData(id, requireNotNull(role.name), requireNotNull(role.code), role.sort ?: 0, role.status ?: 1, role.remark)
        }
        return responseBuilder.ok().data(rs).build()
    }

    /** 兼容原有 snake_case 分页参数。 */
    @GetMapping(params = ["page_size"])
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN')) and hasAuthority('role:read')")
    fun listPaged(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(name = "page_size", defaultValue = "20") pageSize: Int,
    ): ResponseEntity<Response> = responseBuilder.ok().data(service.list(page, pageSize)).build()

    /** 更新文档约定的业务角色。 */
    @PutMapping("/{id}", consumes = ["application/json"])
    @PreAuthorize("hasRole('SUPER_ADMIN') and hasAuthority('role:manage')")
    fun update(@PathVariable id: Long, @RequestBody body: DocumentRoleRequest): ResponseEntity<Response> {
        data class Response(
            val id: Long,
            val name: String,
            val code: String,
            val sort: Int,
            val status: Int,
            val remark: String?,
        )

        val current = documentRoles[id] ?: throw IllegalArgumentException("角色不存在")
        val role = DocumentRoleRequest(
            body.name ?: current.name,
            body.code ?: current.code,
            body.sort ?: current.sort,
            body.status ?: current.status,
            body.remark ?: current.remark,
        )
        require(documentRoles.entries.none { it.key != id && it.value.code.equals(role.code, true) }) { "角色编码已存在" }
        documentRoles[id] = role
        val rs = Response(id, requireNotNull(role.name), requireNotNull(role.code), role.sort ?: 0, role.status ?: 1, role.remark)
        return responseBuilder.ok().data(rs).build()
    }

    /** 批量更新实体记录。 */
    @PutMapping("/batch")
    @PreAuthorize("hasRole('SUPER_ADMIN') and hasAuthority('role:manage')")
    fun updateBatch(@RequestBody entities: List<Role>): ResponseEntity<Response> =
        responseBuilder.ok().data(service.updateBatch(entities)).build()

    /** 删除指定主键的实体记录。 */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') and hasAuthority('role:manage')")
    fun delete(@PathVariable id: Long): ResponseEntity<Response> {
        if (documentRoles.remove(id) == null) service.delete(id)
        return responseBuilder.ok().data(mapOf("id" to id)).build()
    }

    /** 批量删除实体记录。 */
    @DeleteMapping("/batch")
    @PreAuthorize("hasRole('SUPER_ADMIN') and hasAuthority('role:manage')")
    fun deleteBatch(@RequestParam id: List<Long>): ResponseEntity<Response> {
        service.deleteBatch(id)
        return responseBuilder.ok().data(mapOf("ids" to id)).build()
    }
}
