package top.foxball.cartask.controller

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import top.foxball.cartask.service.UserService
import top.foxball.cartask.shared.Response
import top.foxball.cartask.shared.ResponseBuilder
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/users")
class UserController(
    private val userService: UserService,
    private val responseBuilder: ResponseBuilder
) {
    @PostMapping
    fun create(
        @RequestParam username: String,
        @RequestParam email: String,
        @RequestParam credential: String,
        @RequestParam(defaultValue = "USER") role: String,
        @RequestParam(defaultValue = "true") enabled: Boolean
    ): ResponseEntity<Response> {
        data class Response(
            val id: Long,
            val username: String,
            val email: String,
            val role: String,
            val enabled: Boolean,
            @param:JsonProperty("created_at") val createdAt: LocalDateTime,
            @param:JsonProperty("updated_at") val updatedAt: LocalDateTime
        )
        
        val user = userService.create(UserService.CreateCommand(username, email, credential, role, enabled))
        val rs = Response(user.id, user.username, user.email, user.role, user.enabled, user.createdAt, user.updatedAt)
        return responseBuilder.created().data(rs).build()
    }
    
    @PostMapping("/batch")
    fun createBatch(
        @RequestParam username: List<String>,
        @RequestParam email: List<String>,
        @RequestParam credential: List<String>,
        @RequestParam(defaultValue = "USER") role: List<String>,
        @RequestParam(defaultValue = "true") enabled: List<Boolean>
    ): ResponseEntity<Response> {
        data class UserData(
            val id: Long,
            val username: String,
            val email: String,
            val role: String,
            val enabled: Boolean,
            @param:JsonProperty("created_at") val createdAt: LocalDateTime,
            @param:JsonProperty("updated_at") val updatedAt: LocalDateTime
        )
        
        data class Response(val users: List<UserData>)
        require(username.size == email.size && email.size == credential.size && credential.size == role.size && role.size == enabled.size) { "批量用户字段数量必须一致" }
        val users = userService.createBatch(username.indices.map {
            UserService.CreateCommand(
                username[it],
                email[it],
                credential[it],
                role[it],
                enabled[it]
            )
        })
        val rs = Response(users.map {
            UserData(
                it.id,
                it.username,
                it.email,
                it.role,
                it.enabled,
                it.createdAt,
                it.updatedAt
            )
        })
        return responseBuilder.created().data(rs).build()
    }
    
    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): ResponseEntity<Response> {
        data class Response(
            val id: Long,
            val username: String,
            val email: String,
            val role: String,
            val enabled: Boolean,
            @param:JsonProperty("created_at") val createdAt: LocalDateTime,
            @param:JsonProperty("updated_at") val updatedAt: LocalDateTime
        )
        
        val user = userService.get(id)
        val rs = Response(user.id, user.username, user.email, user.role, user.enabled, user.createdAt, user.updatedAt)
        return responseBuilder.ok().data(rs).build()
    }
    
    @GetMapping
    fun list(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(name = "page_size", defaultValue = "20") pageSize: Int
    ): ResponseEntity<Response> {
        data class UserData(
            val id: Long,
            val username: String,
            val email: String,
            val role: String,
            val enabled: Boolean,
            @param:JsonProperty("created_at") val createdAt: LocalDateTime,
            @param:JsonProperty("updated_at") val updatedAt: LocalDateTime
        )
        
        data class Response(
            val users: List<UserData>,
            val page: Int,
            @param:JsonProperty("page_size") val pageSize: Int,
            val total: Long
        )
        
        val result = userService.list(page, pageSize)
        val rs = Response(result.users.map {
            UserData(
                it.id,
                it.username,
                it.email,
                it.role,
                it.enabled,
                it.createdAt,
                it.updatedAt
            )
        }, result.page, result.pageSize, result.total)
        return responseBuilder.ok().data(rs).build()
    }
    
    @GetMapping("/batch")
    fun getBatch(@RequestParam id: List<Long>): ResponseEntity<Response> {
        data class UserData(
            val id: Long,
            val username: String,
            val email: String,
            val role: String,
            val enabled: Boolean,
            @param:JsonProperty("created_at") val createdAt: LocalDateTime,
            @param:JsonProperty("updated_at") val updatedAt: LocalDateTime
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
                it.createdAt,
                it.updatedAt
            )
        })
        return responseBuilder.ok().data(rs).build()
    }
    
    @PutMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @RequestParam(required = false) username: String?,
        @RequestParam(required = false) email: String?,
        @RequestParam(required = false) credential: String?,
        @RequestParam(required = false) role: String?,
        @RequestParam(required = false) enabled: Boolean?
    ): ResponseEntity<Response> {
        data class Response(
            val id: Long,
            val username: String,
            val email: String,
            val role: String,
            val enabled: Boolean
        )
        
        val user = userService.update(id, UserService.UpdateCommand(username, email, credential, role, enabled))
        val rs = Response(user.id, user.username, user.email, user.role, user.enabled)
        return responseBuilder.ok().data(rs).build()
    }
    
    @PutMapping("/batch")
    fun updateBatch(
        @RequestParam id: List<Long>,
        @RequestParam(required = false) username: String?,
        @RequestParam(required = false) email: String?,
        @RequestParam(required = false) credential: String?,
        @RequestParam(required = false) role: String?,
        @RequestParam(required = false) enabled: Boolean?
    ): ResponseEntity<Response> {
        data class Response(@param:JsonProperty("user_ids") val userIds: List<Long>)
        userService.updateBatch(id, UserService.UpdateCommand(username, email, credential, role, enabled))
        val rs = Response(id)
        return responseBuilder.ok().data(rs).build()
    }
    
    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long): ResponseEntity<Response> {
        data class Response(val id: Long)
        userService.delete(id)
        val rs = Response(id)
        return responseBuilder.ok().data(rs).build()
    }
    
    @DeleteMapping("/batch")
    fun deleteBatch(@RequestParam id: List<Long>): ResponseEntity<Response> {
        data class Response(val ids: List<Long>)
        userService.deleteBatch(id)
        val rs = Response(id)
        return responseBuilder.ok().data(rs).build()
    }
}
