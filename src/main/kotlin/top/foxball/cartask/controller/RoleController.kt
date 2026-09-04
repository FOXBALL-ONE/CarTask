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
import top.foxball.cartask.entity.Role
import top.foxball.cartask.authentication.SecurityRole
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
        val status = requireNotNull(body.status) { "状态不能为空" }
        require(status == 0 || status == 1) { "状态必须为 0 或 1" }
        require(!service.list(1, 100).content.any { it.name.equals(code, true) }) { "角色编码已存在" }
        val role = Role().apply {
            this.name = code
            description = name
            enabled = status != 0
            documentSort = requireNotNull(body.sort) { "排序不能为空" }
            documentRemark = body.remark
        }
        val saved = service.create(role)
        val rs = Response(requireNotNull(saved.id), name, saved.name, saved.documentSort ?: 0, if (saved.enabled) 1 else 0, saved.documentRemark)
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
    fun get(@PathVariable id: Long): ResponseEntity<Response> {
        data class Response(val id: Long, val name: String, val code: String, val sort: Int, val status: Int, val remark: String?)
        val role = service.get(id)
        val rs = Response(requireNotNull(role.id), role.description ?: role.name, role.documentCode ?: role.name, role.documentSort ?: 0, role.documentStatus ?: if (role.enabled) 1 else 0, role.documentRemark)
        return responseBuilder.ok().data(rs).build()
    }

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

        val allRoles = mutableListOf<Role>()
        var sourcePage = 1
        var sourceTotal = 0L
        do {
            val source = service.list(sourcePage, 100)
            allRoles += source.content
            sourceTotal = source.totalElements
            sourcePage++
        } while (allRoles.size < sourceTotal)
        val systemRoles = allRoles.map {
            RoleData(
                requireNotNull(it.id), it.description ?: it.name, it.documentCode ?: it.name,
                it.documentSort ?: 0, it.documentStatus ?: if (it.enabled) 1 else 0, it.documentRemark,
            )
        }
        val rs = systemRoles
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

        require(body.status == null || body.status == 0 || body.status == 1) { "状态必须为 0 或 1" }
        val current = service.get(id)
        val role = Role().apply {
            this.id = id
            name = if (SecurityRole.normalizeOrNull(current.name) == null) body.code ?: current.name else current.name
            description = body.name ?: current.description
            enabled = body.status?.let { it != 0 } ?: current.enabled
            documentSort = body.sort ?: current.documentSort ?: 0
            documentRemark = body.remark ?: current.documentRemark
            permissions = current.permissions
        }
        val saved = service.update(id, role)
        val rs = Response(id, saved.description ?: saved.name, saved.name, saved.documentSort ?: 0, if (saved.enabled) 1 else 0, saved.documentRemark)
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
        service.delete(id)
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
