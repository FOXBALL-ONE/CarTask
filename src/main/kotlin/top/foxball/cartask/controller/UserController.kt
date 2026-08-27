package top.foxball.cartask.controller

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import top.foxball.cartask.entity.User
import top.foxball.cartask.service.UserService
import top.foxball.cartask.shared.Response
import top.foxball.cartask.shared.ResponseBuilder
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/users")
/** 用户账户的单条和批量管理接口。 */
class UserController(
    private val userService: UserService,
    private val responseBuilder: ResponseBuilder,
) {
    /** 文档兼容的 JSON 用户创建入口。 */
    @PostMapping(consumes = ["application/json"])
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN')) and hasAuthority('user:create')")
    fun createDocument(@RequestBody body: DocumentUserRequest): ResponseEntity<Response> {
        data class Response(
            val id: Long,
            val username: String,
            val name: String?,
            @param:JsonProperty("deptId") val deptId: Long?,
            val phone: String?,
            val email: String,
            @param:JsonProperty("roleIds") val roleIds: List<Long>,
            val status: Int,
            @param:JsonProperty("createTime") val createTime: LocalDateTime,
        )
        val username = requireNotNull(body.username) { "用户名不能为空" }
        val password = requireNotNull(body.password) { "密码不能为空" }
        val user = userService.create(
            UserService.CreateCommand(
                username = username,
                email = body.email ?: "$username@local.invalid",
                credential = password,
                phone = body.phone,
                departmentId = body.deptId,
                status = if (body.status == 0) User.Status.BANNED else User.Status.Activity,
                nickName = body.name,
            ),
        )
        val rs = Response(user.id, user.username, body.name, user.departmentId, user.phone, user.email, body.roleIds.orEmpty(), if (user.status == User.Status.Activity) 1 else 0, user.createdAt)
        return responseBuilder.created().data(rs).build()
    }

    /** 文档兼容的 JSON 用户更新入口。 */
    @PutMapping("/{id}", consumes = ["application/json"])
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN')) and hasAuthority('user:update')")
    fun updateDocument(@PathVariable id: Long, @RequestBody body: DocumentUserRequest): ResponseEntity<Response> {
        data class Response(
            val id: Long,
            val username: String,
            val name: String?,
            @param:JsonProperty("deptId") val deptId: Long?,
            val phone: String?,
            val email: String,
            @param:JsonProperty("roleIds") val roleIds: List<Long>,
            val status: Int,
            @param:JsonProperty("createTime") val createTime: LocalDateTime,
        )
        val user = userService.update(
            id,
            UserService.UpdateCommand(
                username = body.username,
                email = body.email,
                credential = body.password,
                phone = body.phone,
                departmentId = body.deptId,
                status = body.status?.let { if (it == 0) User.Status.BANNED else User.Status.Activity },
                nickName = body.name,
            ),
        )
        val rs = Response(user.id, user.username, body.name, user.departmentId, user.phone, user.email, body.roleIds.orEmpty(), if (user.status == User.Status.Activity) 1 else 0, user.createdAt)
        return responseBuilder.ok().data(rs).build()
    }

    /** 创建一个用户账户。 */
    @PostMapping
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN')) and hasAuthority('user:create')")
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

    /** 根据并列参数批量创建用户账户。 */
    @PostMapping("/batch")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN')) and hasAuthority('user:create')")
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
                it.status,
                it.createdAt,
                it.updatedAt,
            )
        })
        return responseBuilder.created().data(rs).build()
    }

    /** 按用户 ID 查询账户信息。 */
    @GetMapping("/{id}")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN')) and hasAuthority('user:read')")
    fun get(@PathVariable id: Long): ResponseEntity<Response> {
        data class Response(
            val id: Long,
            val username: String,
            val name: String?,
            @param:JsonProperty("deptId") val deptId: Long?,
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
            user.phone,
            user.email,
            emptyList(),
            if (user.status == User.Status.Activity) 1 else 0,
            user.createdAt,
        )
        return responseBuilder.ok().data(rs).build()
    }

    /** 分页查询用户账户。 */
    @GetMapping
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN')) and hasAuthority('user:read')")
    fun listDocument(
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) status: Int?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(name = "pageSize", defaultValue = "8") pageSize: Int,
    ): ResponseEntity<Response> {
        data class UserData(
            val id: Long,
            val username: String,
            val name: String?,
            @param:JsonProperty("deptId") val deptId: Long?,
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
        val users = userService.list(1, 100).users.filter {
            (keyword.isNullOrBlank() || it.username.contains(keyword, true) || it.name.orEmpty().contains(keyword, true) || it.phone.orEmpty().contains(keyword, true)) &&
                (status == null || (if (it.status == User.Status.Activity) 1 else 0) == status)
        }
        val from = ((page - 1) * pageSize).coerceAtMost(users.size)
        val to = (from + pageSize).coerceAtMost(users.size)
        val rs = Response(
            users.subList(from, to).map {
                UserData(
                    it.id, it.username, it.name, it.departmentId, it.phone, it.email, emptyList(),
                    if (it.status == User.Status.Activity) 1 else 0, it.createdAt,
                )
            },
            users.size,
            page,
            pageSize,
        )
        return responseBuilder.ok().data(rs).build()
    }

    /** 兼容原有 snake_case 分页参数。 */
    @GetMapping(params = ["page_size"])
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN')) and hasAuthority('user:read')")
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
                it.status,
                it.createdAt,
                it.updatedAt,
            )
        }, result.page, result.pageSize, result.total)
        return responseBuilder.ok().data(rs).build()
    }

    /** 按多个用户 ID 批量查询账户信息。 */
    @GetMapping("/batch")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN')) and hasAuthority('user:read')")
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

    /** 更新指定用户账户的可变字段。 */
    @PutMapping("/{id}")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN')) and hasAuthority('user:update')")
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
            UserService.UpdateCommand(username, email, credential, role, enabled, phone, gender, departmentId, positionId, status),
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

    /** 对多个用户应用相同的更新内容。 */
    @PutMapping("/batch")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN')) and hasAuthority('user:update')")
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
            UserService.UpdateCommand(username, email, credential, role, enabled, phone, gender, departmentId, positionId, status),
        )
        val rs = Response(id)
        return responseBuilder.ok().data(rs).build()
    }

    /** 仅超级管理员可变更账户角色，避免普通资料更新形成提权路径。 */
    @PutMapping("/{id}/role")
    @PreAuthorize("hasRole('SUPER_ADMIN') and hasAuthority('user:role-assign')")
    fun assignRole(
        @PathVariable id: Long,
        @RequestParam role: String,
    ): ResponseEntity<Response> {
        data class Response(val id: Long, val role: String)

        val user = userService.update(id, UserService.UpdateCommand(role = role))
        val rs = Response(user.id, user.role)
        return responseBuilder.ok().data(rs).build()
    }

    /** 启停或封禁账户会撤销旧会话，独立于普通资料更新。 */
    @PutMapping("/{id}/account-status")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN')) and hasAuthority('user:disable')")
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

    /** 删除指定用户账户。 */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') and hasAuthority('user:disable')")
    fun delete(@PathVariable id: Long): ResponseEntity<Response> {
        data class Response(val id: Long)
        userService.delete(id)
        val rs = Response(id)
        return responseBuilder.ok().data(rs).build()
    }

    /** 按多个用户 ID 批量删除账户。 */
    @DeleteMapping("/batch")
    @PreAuthorize("hasRole('SUPER_ADMIN') and hasAuthority('user:disable')")
    fun deleteBatch(@RequestParam id: List<Long>): ResponseEntity<Response> {
        data class Response(@param:JsonProperty("user_ids") val userIds: List<Long>)
        userService.deleteBatch(id)
        val rs = Response(id)
        return responseBuilder.ok().data(rs).build()
    }
}
