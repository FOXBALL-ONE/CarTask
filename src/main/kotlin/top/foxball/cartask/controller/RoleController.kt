package top.foxball.cartask.controller

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import top.foxball.cartask.authentication.SecurityPermission
import top.foxball.cartask.authentication.SecurityRole
import top.foxball.cartask.entity.Role
import top.foxball.cartask.repository.PermissionRepository
import top.foxball.cartask.repository.RoleRepository
import top.foxball.cartask.service.RoleService
import top.foxball.cartask.shared.Response
import top.foxball.cartask.shared.ResponseBuilder

@RestController
@RequestMapping("/api/roles")

/** class RoleController：Web 控制器，负责接收 HTTP 请求、调用领域服务并构造统一响应。 */
class RoleController(
    private val service: RoleService,
    private val roleRepository: RoleRepository,
    private val permissionRepository: PermissionRepository,
    private val responseBuilder: ResponseBuilder,
) {
    
    @PostMapping(consumes = ["application/json"])
    @PreAuthorize("hasRole('SUPER_ADMIN') and hasAuthority('role:manage')")
            /** create：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
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
            body.permissions?.let { codes ->
                val normalizedCodes = codes.map(SecurityPermission::normalize)
                require(normalizedCodes.distinct().size == normalizedCodes.size) { "权限编码不能重复" }
                val resolved = permissionRepository.findAllByCodeIn(normalizedCodes)
                require(resolved.size == normalizedCodes.size) { "包含不存在的权限编码" }
                require(resolved.all { it.enabled }) { "不能授予已停用的权限" }
                val byCode = resolved.associateBy { SecurityPermission.normalize(it.code) }
                permissions = normalizedCodes.map(byCode::getValue).toMutableSet()
            }
        }
        val saved = service.create(role)
        val rs = Response(
            requireNotNull(saved.id),
            name,
            saved.name,
            saved.documentSort ?: 0,
            if (saved.enabled) 1 else 0,
            saved.documentRemark
        )
        return responseBuilder.created().data(rs).build()
    }
    
    
    @PostMapping("/batch")
    @PreAuthorize("hasRole('SUPER_ADMIN') and hasAuthority('role:manage')")
            /** createBatch：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun createBatch(@RequestBody entities: List<Role>): ResponseEntity<Response> =
        responseBuilder.created().data(service.createBatch(entities)).build()
    
    
    @GetMapping("/{id}")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('role:read')")
            /** get：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun get(@PathVariable id: Long): ResponseEntity<Response> {
        data class Response(
            val id: Long,
            val name: String,
            val code: String,
            val sort: Int,
            val status: Int,
            val remark: String?,
            @param:JsonProperty("permission_codes") val permissionCodes: List<String>,
        )
        
        val role = roleRepository.findById(id).orElseThrow { IllegalArgumentException("角色不存在") }
        val loadedRole = roleRepository.findByNameIgnoreCase(role.name) ?: role
        val rs = Response(
            requireNotNull(loadedRole.id),
            loadedRole.description ?: loadedRole.name,
            loadedRole.documentCode ?: loadedRole.name,
            loadedRole.documentSort ?: 0,
            loadedRole.documentStatus ?: if (loadedRole.enabled) 1 else 0,
            loadedRole.documentRemark,
            loadedRole.permissions.map { it.code }.sorted()
        )
        return responseBuilder.ok().data(rs).build()
    }
    
    
    @GetMapping("/batch")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('role:read')")
            /** getBatch：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun getBatch(@RequestParam id: List<Long>): ResponseEntity<Response> =
        responseBuilder.ok().data(service.getBatch(id)).build()
    
    
    @GetMapping
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('role:read')")
            /** list：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun list(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(name = "pageSize", defaultValue = "8") pageSize: Int,
    ): ResponseEntity<Response> {
        data class RoleData(
            val id: Long,
            val name: String,
            val code: String,
            val sort: Int,
            val status: Int,
            val remark: String?,
        )
        
        data class Response(val items: List<RoleData>, val total: Int)
        
        require(page >= 1) { "页码必须大于 0" }
        require(pageSize in 1..100) { "每页数量必须在 1 到 100 之间" }
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
        val from = ((page - 1) * pageSize).coerceAtMost(systemRoles.size)
        val to = (from + pageSize).coerceAtMost(systemRoles.size)
        val rs = Response(systemRoles.subList(from, to), systemRoles.size)
        return responseBuilder.ok().data(rs).build()
    }
    
    
    @GetMapping(params = ["page_size"])
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('role:read')")
            /** listPaged：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun listPaged(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(name = "page_size", defaultValue = "20") pageSize: Int,
    ): ResponseEntity<Response> = responseBuilder.ok().data(service.list(page, pageSize)).build()
    
    
    @PutMapping("/{id}", consumes = ["application/json"])
    @PreAuthorize("hasRole('SUPER_ADMIN') and hasAuthority('role:manage')")
            /** update：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
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
            body.permissions?.let { codes ->
                val normalizedCodes = codes.map(SecurityPermission::normalize)
                require(normalizedCodes.distinct().size == normalizedCodes.size) { "权限编码不能重复" }
                val resolved = permissionRepository.findAllByCodeIn(normalizedCodes)
                require(resolved.size == normalizedCodes.size) { "包含不存在的权限编码" }
                require(resolved.all { it.enabled }) { "不能授予已停用的权限" }
                val byCode = resolved.associateBy { SecurityPermission.normalize(it.code) }
                permissions = normalizedCodes.map(byCode::getValue).toMutableSet()
            }
        }
        val saved = service.update(id, role)
        val rs = Response(
            id,
            saved.description ?: saved.name,
            saved.name,
            saved.documentSort ?: 0,
            if (saved.enabled) 1 else 0,
            saved.documentRemark
        )
        return responseBuilder.ok().data(rs).build()
    }
    
    
    @PutMapping("/{id}/permissions", consumes = ["application/json"])
    @PreAuthorize("hasRole('SUPER_ADMIN') and hasAuthority('role:manage')")
            /** replacePermissions：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun replacePermissions(
        @PathVariable id: Long,
        @RequestBody permissionCodes: List<String>,
    ): ResponseEntity<Response> {
        data class Response(
            val id: Long,
            @param:JsonProperty("permission_codes") val permissionCodes: List<String>,
        )
        
        val normalizedCodes = permissionCodes.map(SecurityPermission::normalize)
        require(normalizedCodes.distinct().size == normalizedCodes.size) { "权限编码不能重复" }
        val resolvedPermissions = permissionRepository.findAllByCodeIn(normalizedCodes)
        require(resolvedPermissions.size == normalizedCodes.size) { "包含不存在的权限编码" }
        require(resolvedPermissions.all { it.enabled }) { "不能授予已停用的权限" }
        val byCode = resolvedPermissions.associateBy { SecurityPermission.normalize(it.code) }
        val permissions = normalizedCodes.map(byCode::getValue).toMutableSet()
        val current = service.get(id)
        val role = Role().apply {
            this.id = id
            name = current.name
            description = current.description
            enabled = current.enabled
            documentCode = current.documentCode
            documentSort = current.documentSort
            documentRemark = current.documentRemark
            documentStatus = current.documentStatus
            this.permissions = permissions
        }
        val saved = service.update(id, role)
        val rs = Response(requireNotNull(saved.id), saved.permissions.map { it.code }.sorted())
        return responseBuilder.ok().data(rs).build()
    }
    
    
    @PutMapping("/batch")
    @PreAuthorize("hasRole('SUPER_ADMIN') and hasAuthority('role:manage')")
            /** updateBatch：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun updateBatch(@RequestBody entities: List<Role>): ResponseEntity<Response> =
        responseBuilder.ok().data(service.updateBatch(entities)).build()
    
    
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') and hasAuthority('role:manage')")
            /** delete：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun delete(@PathVariable id: Long): ResponseEntity<Response> {
        service.delete(id)
        return responseBuilder.ok().data(mapOf("id" to id)).build()
    }
    
    
    @DeleteMapping("/batch")
    @PreAuthorize("hasRole('SUPER_ADMIN') and hasAuthority('role:manage')")
            /** deleteBatch：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun deleteBatch(@RequestParam id: List<Long>): ResponseEntity<Response> {
        service.deleteBatch(id)
        return responseBuilder.ok().data(mapOf("ids" to id)).build()
    }
}
