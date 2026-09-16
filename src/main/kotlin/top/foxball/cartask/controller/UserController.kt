package top.foxball.cartask.controller

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import top.foxball.cartask.entity.User
import top.foxball.cartask.scope.ManagedDepartmentInput
import top.foxball.cartask.scope.ScopeGuard
import top.foxball.cartask.scope.WorkingDepartmentService
import top.foxball.cartask.service.UserService
import top.foxball.cartask.shared.Response
import top.foxball.cartask.shared.ResponseBuilder
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/users")

/** class UserController：Web 控制器，负责接收 HTTP 请求、调用领域服务并构造统一响应。 */
class UserController(
    private val userService: UserService,
    private val responseBuilder: ResponseBuilder,
    private val scopeGuard: ScopeGuard,
    private val workingDepartmentService: WorkingDepartmentService,
) {
    
    @PostMapping(consumes = ["application/json"])
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('user:create')")
            /** createDocument：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun createDocument(@RequestBody body: DocumentUserRequest): ResponseEntity<Response> {
        data class Response(
            val id: Long,
            val username: String,
            val name: String?,
            @param:JsonProperty("deptId") val deptId: Long?,
            @param:JsonProperty("positionId") val positionId: Long?,
            @param:JsonProperty("jobTitle") val jobTitle: String?,
            val phone: String?,
            val email: String,
            @param:JsonProperty("roleIds") val roleIds: List<Long>,
            val status: Int,
            @param:JsonProperty("createTime") val createTime: LocalDateTime,
        )
        
        val username = requireNotNull(body.username) { "用户名不能为空" }
        val password = requireNotNull(body.password) { "密码不能为空" }
        val name = requireNotNull(body.name) { "姓名不能为空" }
        val deptId = requireNotNull(body.deptId) { "部门不能为空" }
        val phone = requireNotNull(body.phone) { "手机号不能为空" }
        val roleIds = requireNotNull(body.roleIds) { "角色不能为空" }
        require(roleIds.isNotEmpty()) { "角色不能为空" }
        val status = requireNotNull(body.status) { "状态不能为空" }
        require(status == 0 || status == 1) { "状态必须为 0 或 1" }
        val user = userService.create(
            UserService.CreateCommand(
                username = username,
                email = body.email ?: "$username@local.invalid",
                credential = password,
                phone = phone,
                departmentId = deptId,
                positionId = body.positionId,
                jobTitle = body.jobTitle,
                status = if (status == 0) User.Status.BANNED else User.Status.Activity,
                nickName = name,
                roleIds = roleIds,
            ),
        )
        val rs = Response(
            user.id,
            user.username,
            user.name,
            user.departmentId,
            user.positionId,
            user.jobTitle,
            user.phone,
            user.email,
            user.roleIds,
            if (user.status == User.Status.Activity) 1 else 0,
            user.createdAt
        )
        return responseBuilder.created().data(rs).build()
    }
    
    
    @PutMapping("/{id}", consumes = ["application/json"])
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('user:update')")
            /** updateDocument：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun updateDocument(@PathVariable id: Long, @RequestBody body: DocumentUserRequest): ResponseEntity<Response> {
        data class Response(
            val id: Long,
            val username: String,
            val name: String?,
            @param:JsonProperty("deptId") val deptId: Long?,
            @param:JsonProperty("positionId") val positionId: Long?,
            @param:JsonProperty("jobTitle") val jobTitle: String?,
            val phone: String?,
            val email: String,
            @param:JsonProperty("roleIds") val roleIds: List<Long>,
            val status: Int,
            @param:JsonProperty("createTime") val createTime: LocalDateTime,
        )
        require(body.status == null || body.status == 0 || body.status == 1) { "状态必须为 0 或 1" }
        val user = userService.update(
            id,
            UserService.UpdateCommand(
                username = body.username,
                email = body.email,
                credential = body.password,
                phone = body.phone,
                departmentId = body.deptId,
                positionId = body.positionId,
                jobTitle = body.jobTitle,
                status = body.status?.let { if (it == 0) User.Status.BANNED else User.Status.Activity },
                nickName = body.name,
                roleIds = body.roleIds,
            ),
        )
        val rs = Response(
            user.id,
            user.username,
            user.name,
            user.departmentId,
            user.positionId,
            user.jobTitle,
            user.phone,
            user.email,
            user.roleIds,
            if (user.status == User.Status.Activity) 1 else 0,
            user.createdAt
        )
        return responseBuilder.ok().data(rs).build()
    }
    
    
    @PostMapping
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('user:create')")
            /** create：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun create(
        @RequestParam username: String,
        @RequestParam email: String,
        @RequestParam credential: String,
        @RequestParam(defaultValue = "USER") role: String,
        @RequestParam(defaultValue = "true") enabled: Boolean,
        @RequestParam(required = false) phone: String?,
        @RequestParam(defaultValue = "UNKNOWN") gender: User.Gender,
        @RequestParam(name = "department_id", required = false) departmentId: Long?,
        @RequestParam(name = "position_id", required = false) positionId: Long?,
        @RequestParam(defaultValue = "Activity") status: User.Status,
    ): ResponseEntity<Response> {
        data class Response(
            val id: Long,
            val username: String,
            val email: String,
            val role: String,
            val enabled: Boolean,
            val phone: String?,
            val gender: User.Gender,
            @param:JsonProperty("department_id") val departmentId: Long?,
            @param:JsonProperty("position_id") val positionId: Long?,
            val status: User.Status,
            @param:JsonProperty("created_at") val createdAt: LocalDateTime,
            @param:JsonProperty("updated_at") val updatedAt: LocalDateTime,
        )
        
        val user = userService.create(
            UserService.CreateCommand(
                username,
                email,
                credential,
                role,
                enabled,
                phone,
                gender,
                departmentId,
                positionId,
                status,
            ),
        )
        val rs = Response(
            user.id,
            user.username,
            user.email,
            user.role,
            user.enabled,
            user.phone,
            user.gender,
            user.departmentId,
            user.positionId,
            user.status,
            user.createdAt,
            user.updatedAt,
        )
        return responseBuilder.created().data(rs).build()
    }
    
    
    @PostMapping("/batch")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('user:create')")
            /** createBatch：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun createBatch(
        @RequestParam username: List<String>,
        @RequestParam email: List<String>,
        @RequestParam credential: List<String>,
        @RequestParam(defaultValue = "USER") role: List<String>,
        @RequestParam(defaultValue = "true") enabled: List<Boolean>,
        @RequestParam(required = false) phone: List<String>?,
        @RequestParam(required = false) gender: List<User.Gender>?,
        @RequestParam(name = "department_id", required = false) departmentId: List<Long>?,
        @RequestParam(name = "position_id", required = false) positionId: List<Long>?,
        @RequestParam(required = false) status: List<User.Status>?,
    ): ResponseEntity<Response> {
        data class UserData(
            val id: Long,
            val username: String,
            val email: String,
            val role: String,
            val enabled: Boolean,
            val phone: String?,
            val gender: User.Gender,
            @param:JsonProperty("department_id") val departmentId: Long?,
            @param:JsonProperty("position_id") val positionId: Long?,
            @param:JsonProperty("job_title") val jobTitle: String?,
            val status: User.Status,
            @param:JsonProperty("created_at") val createdAt: LocalDateTime,
            @param:JsonProperty("updated_at") val updatedAt: LocalDateTime,
        )
        
        data class Response(val users: List<UserData>)
        
        require(username.isNotEmpty()) { "用户列表不能为空" }
        require(username.size == email.size && email.size == credential.size && credential.size == role.size && role.size == enabled.size) {
            "批量用户必填字段数量必须一致"
        }
        require(phone == null || phone.size == username.size) { "手机号数量必须与用户数量一致" }
        require(gender == null || gender.size == username.size) { "性别数量必须与用户数量一致" }
        require(departmentId == null || departmentId.size == username.size) { "部门数量必须与用户数量一致" }
        require(positionId == null || positionId.size == username.size) { "职位数量必须与用户数量一致" }
        require(status == null || status.size == username.size) { "状态数量必须与用户数量一致" }
        val users = userService.createBatch(username.indices.map {
            UserService.CreateCommand(
                username[it],
                email[it],
                credential[it],
                role[it],
                enabled[it],
                phone?.getOrNull(it),
                gender?.getOrNull(it) ?: User.Gender.UNKNOWN,
                departmentId?.getOrNull(it),
                positionId?.getOrNull(it),
                status?.getOrNull(it) ?: User.Status.Activity,
            )
        })
        val rs = Response(users.map {
            UserData(
                it.id,
                it.username,
                it.email,
                it.role,
                it.enabled,
                it.phone,
                it.gender,
                it.departmentId,
                it.positionId,
                it.jobTitle,
                it.status,
                it.createdAt,
                it.updatedAt,
            )
        })
        return responseBuilder.created().data(rs).build()
    }
    
    
    @GetMapping("/{id}")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('user:read')")
            /** get：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun get(@PathVariable id: Long): ResponseEntity<Response> {
        data class Response(
            val id: Long,
            val username: String,
            val name: String?,
            @param:JsonProperty("deptId") val deptId: Long?,
            @param:JsonProperty("positionId") val positionId: Long?,
            @param:JsonProperty("jobTitle") val jobTitle: String?,
            val phone: String?,
            val email: String,
            @param:JsonProperty("roleIds") val roleIds: List<Long>,
            val status: Int,
            @param:JsonProperty("createTime") val createTime: LocalDateTime,
        )
        
        val user = userService.get(id)
        val rs = Response(
            user.id,
            user.username,
            user.name,
            user.departmentId,
            user.positionId,
            user.jobTitle,
            user.phone,
            user.email,
            user.roleIds,
            if (user.status == User.Status.Activity) 1 else 0,
            user.createdAt,
        )
        return responseBuilder.ok().data(rs).build()
    }
    
    
    @GetMapping
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('user:read')")
            /** listDocument：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun listDocument(
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) status: Int?,
        @RequestParam(name = "department_id", required = false) departmentId: Long?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(name = "pageSize", defaultValue = "8") pageSize: Int,
    ): ResponseEntity<Response> {
        data class UserData(
            val id: Long,
            val username: String,
            val name: String?,
            @param:JsonProperty("deptId") val deptId: Long?,
            @param:JsonProperty("positionId") val positionId: Long?,
            @param:JsonProperty("jobTitle") val jobTitle: String?,
            val phone: String?,
            val email: String,
            @param:JsonProperty("roleIds") val roleIds: List<Long>,
            val status: Int,
            @param:JsonProperty("createTime") val createTime: LocalDateTime,
        )
        
        data class Response(
            val items: List<UserData>,
            val total: Int,
            val page: Int,
            @param:JsonProperty("pageSize") val pageSize: Int,
        )
        
        require(page >= 1) { "页码必须大于 0" }
        require(pageSize in 1..100) { "每页数量必须在 1 到 100 之间" }
        val allUsers = mutableListOf<UserService.UserData>()
        var sourcePage = 1
        var sourceTotal = 0L
        do {
            val source = userService.list(sourcePage, 100)
            allUsers += source.users
            sourceTotal = source.total
            sourcePage++
        } while (allUsers.size < sourceTotal)
        val scope = scopeGuard.currentScope()
        val requestedFilter = scopeGuard.requestedDepartment(departmentId, scope)
        if (requestedFilter.denied) {
            return responseBuilder.ok().data(Response(emptyList(), 0, page, pageSize)).build()
        }
        val users = allUsers.filter {
            (keyword.isNullOrBlank() || it.username.contains(keyword, true) || it.name.orEmpty()
                .contains(keyword, true) || it.phone.orEmpty().contains(keyword, true)) &&
                    (status == null || (if (it.status == User.Status.Activity) 1 else 0) == status) &&
                    (requestedFilter.departmentId == null || it.departmentId == requestedFilter.departmentId)
        }
        val from = ((page - 1) * pageSize).coerceAtMost(users.size)
        val to = (from + pageSize).coerceAtMost(users.size)
        val rs = Response(
            users.subList(from, to).map {
                UserData(
                    it.id,
                    it.username,
                    it.name,
                    it.departmentId,
                    it.positionId,
                    it.jobTitle,
                    it.phone,
                    it.email,
                    it.roleIds,
                    if (it.status == User.Status.Activity) 1 else 0,
                    it.createdAt,
                )
            },
            users.size,
            page,
            pageSize,
        )
        return responseBuilder.ok().data(rs).build()
    }
    
    
    @GetMapping(params = ["page_size"])
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('user:read')")
            /** list：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun list(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(name = "page_size", defaultValue = "20") pageSize: Int,
    ): ResponseEntity<Response> {
        data class UserData(
            val id: Long,
            val username: String,
            val email: String,
            val role: String,
            val enabled: Boolean,
            val phone: String?,
            val gender: User.Gender,
            @param:JsonProperty("department_id") val departmentId: Long?,
            @param:JsonProperty("position_id") val positionId: Long?,
            @param:JsonProperty("job_title") val jobTitle: String?,
            val status: User.Status,
            @param:JsonProperty("created_at") val createdAt: LocalDateTime,
            @param:JsonProperty("updated_at") val updatedAt: LocalDateTime,
        )
        
        data class Response(
            val users: List<UserData>,
            val page: Int,
            @param:JsonProperty("page_size") val pageSize: Int,
            val total: Long,
        )
        
        val result = userService.list(page, pageSize)
        val rs = Response(result.users.map {
            UserData(
                it.id,
                it.username,
                it.email,
                it.role,
                it.enabled,
                it.phone,
                it.gender,
                it.departmentId,
                it.positionId,
                it.jobTitle,
                it.status,
                it.createdAt,
                it.updatedAt,
            )
        }, result.page, result.pageSize, result.total)
        return responseBuilder.ok().data(rs).build()
    }
    
    
    @GetMapping("/batch")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('user:read')")
            /** getBatch：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun getBatch(@RequestParam id: List<Long>): ResponseEntity<Response> {
        data class UserData(
            val id: Long,
            val username: String,
            val email: String,
            val role: String,
            val enabled: Boolean,
            val phone: String?,
            val gender: User.Gender,
            @param:JsonProperty("department_id") val departmentId: Long?,
            @param:JsonProperty("position_id") val positionId: Long?,
            val status: User.Status,
            @param:JsonProperty("created_at") val createdAt: LocalDateTime,
            @param:JsonProperty("updated_at") val updatedAt: LocalDateTime,
        )
        
        data class Response(val users: List<UserData>)
        
        val users = userService.getBatch(id)
        val rs = Response(users.map {
            UserData(
                it.id,
                it.username,
                it.email,
                it.role,
                it.enabled,
                it.phone,
                it.gender,
                it.departmentId,
                it.positionId,
                it.status,
                it.createdAt,
                it.updatedAt,
            )
        })
        return responseBuilder.ok().data(rs).build()
    }
    
    
    @PutMapping("/{id}")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('user:update')")
            /** update：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun update(
        @PathVariable id: Long,
        @RequestParam(required = false) username: String?,
        @RequestParam(required = false) email: String?,
        @RequestParam(required = false) credential: String?,
        @RequestParam(required = false) role: String?,
        @RequestParam(required = false) enabled: Boolean?,
        @RequestParam(required = false) phone: String?,
        @RequestParam(required = false) gender: User.Gender?,
        @RequestParam(name = "department_id", required = false) departmentId: Long?,
        @RequestParam(name = "position_id", required = false) positionId: Long?,
        @RequestParam(required = false) status: User.Status?,
    ): ResponseEntity<Response> {
        data class Response(
            val id: Long,
            val username: String,
            val email: String,
            val role: String,
            val enabled: Boolean,
            val phone: String?,
            val gender: User.Gender,
            @param:JsonProperty("department_id") val departmentId: Long?,
            @param:JsonProperty("position_id") val positionId: Long?,
            val status: User.Status,
        )
        
        require(role == null && enabled == null && status == null) { "角色和账号状态必须使用专用接口更新" }
        val user = userService.update(
            id,
            UserService.UpdateCommand(
                username,
                email,
                credential,
                role,
                enabled,
                phone,
                gender,
                departmentId,
                positionId,
                status
            ),
        )
        val rs = Response(
            user.id,
            user.username,
            user.email,
            user.role,
            user.enabled,
            user.phone,
            user.gender,
            user.departmentId,
            user.positionId,
            user.status,
        )
        return responseBuilder.ok().data(rs).build()
    }
    
    
    @PutMapping("/batch")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('user:update')")
            /** updateBatch：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun updateBatch(
        @RequestParam id: List<Long>,
        @RequestParam(required = false) username: String?,
        @RequestParam(required = false) email: String?,
        @RequestParam(required = false) credential: String?,
        @RequestParam(required = false) role: String?,
        @RequestParam(required = false) enabled: Boolean?,
        @RequestParam(required = false) phone: String?,
        @RequestParam(required = false) gender: User.Gender?,
        @RequestParam(name = "department_id", required = false) departmentId: Long?,
        @RequestParam(name = "position_id", required = false) positionId: Long?,
        @RequestParam(required = false) status: User.Status?,
    ): ResponseEntity<Response> {
        data class Response(@param:JsonProperty("user_ids") val userIds: List<Long>)
        require(role == null && enabled == null && status == null) { "角色和账号状态必须使用专用接口更新" }
        userService.updateBatch(
            id,
            UserService.UpdateCommand(
                username,
                email,
                credential,
                role,
                enabled,
                phone,
                gender,
                departmentId,
                positionId,
                status
            ),
        )
        val rs = Response(id)
        return responseBuilder.ok().data(rs).build()
    }
    
    
    @PutMapping("/{id}/role")
    @PreAuthorize("hasRole('SUPER_ADMIN') and hasAuthority('user:role-assign')")
            /** assignRole：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun assignRole(
        @PathVariable id: Long,
        @RequestParam role: String,
    ): ResponseEntity<Response> {
        data class Response(val id: Long, val role: String)
        
        val user = userService.update(id, UserService.UpdateCommand(role = role))
        val rs = Response(user.id, user.role)
        return responseBuilder.ok().data(rs).build()
    }
    
    
    @PutMapping("/{id}/account-status")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('user:disable')")
            /** updateAccountStatus：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun updateAccountStatus(
        @PathVariable id: Long,
        @RequestParam enabled: Boolean,
        @RequestParam status: User.Status,
    ): ResponseEntity<Response> {
        data class Response(val id: Long, val enabled: Boolean, val status: User.Status)
        
        val user = userService.update(id, UserService.UpdateCommand(enabled = enabled, status = status))
        val rs = Response(user.id, user.enabled, user.status)
        return responseBuilder.ok().data(rs).build()
    }
    
    
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') and hasAuthority('user:disable')")
            /** delete：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun delete(@PathVariable id: Long): ResponseEntity<Response> {
        data class Response(val id: Long)
        userService.delete(id)
        val rs = Response(id)
        return responseBuilder.ok().data(rs).build()
    }
    
    
    @DeleteMapping("/batch")
    @PreAuthorize("hasRole('SUPER_ADMIN') and hasAuthority('user:disable')")
            /** deleteBatch：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun deleteBatch(@RequestParam id: List<Long>): ResponseEntity<Response> {
        data class Response(@param:JsonProperty("user_ids") val userIds: List<Long>)
        userService.deleteBatch(id)
        val rs = Response(id)
        return responseBuilder.ok().data(rs).build()
    }
    
    
    @GetMapping("/{id}/managed-departments")
    @PreAuthorize("hasRole('SUPER_ADMIN') and hasAuthority('user:role-assign')")
            /** managedDepartments：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun managedDepartments(@PathVariable id: Long): ResponseEntity<Response> {
        data class DepartmentData(
            @param:JsonProperty("department_id") val departmentId: Long,
            @param:JsonProperty("department_name") val departmentName: String,
            @param:JsonProperty("include_descendants") val includeDescendants: Boolean,
        )
        
        data class Response(val departments: List<DepartmentData>)
        
        val rs = Response(
            workingDepartmentService.managedDepartmentsOf(id).map {
                DepartmentData(it.departmentId, it.departmentName, it.includeDescendants)
            },
        )
        return responseBuilder.ok().data(rs).build()
    }
    
    
    @PutMapping("/{id}/managed-departments")
    @PreAuthorize("hasRole('SUPER_ADMIN') and hasAuthority('user:role-assign')")
            /** replaceManagedDepartments：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun replaceManagedDepartments(
        @PathVariable id: Long,
        @RequestBody body: ManagedDepartmentRequest,
    ): ResponseEntity<Response> {
        data class DepartmentData(
            @param:JsonProperty("department_id") val departmentId: Long,
            @param:JsonProperty("department_name") val departmentName: String,
            @param:JsonProperty("include_descendants") val includeDescendants: Boolean,
        )
        
        data class Response(val departments: List<DepartmentData>)
        
        val items = body.departments.orEmpty().map { item ->
            ManagedDepartmentInput(
                departmentId = requireNotNull(item.departmentId) { "部门不能为空" },
                includeDescendants = item.includeDescendants,
            )
        }
        val rs = Response(
            workingDepartmentService.replaceManagedDepartments(id, items).map {
                DepartmentData(it.departmentId, it.departmentName, it.includeDescendants)
            },
        )
        return responseBuilder.ok().message("部门管理范围已更新").data(rs).build()
    }
}
