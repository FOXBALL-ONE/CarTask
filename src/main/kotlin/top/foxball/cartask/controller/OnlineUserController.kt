package top.foxball.cartask.controller

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import top.foxball.cartask.entity.User
import top.foxball.cartask.repository.UserRepository
import top.foxball.cartask.service.OnlinePresenceService
import top.foxball.cartask.shared.Response
import top.foxball.cartask.shared.ResponseBuilder
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/online-users")
/** class OnlineUserController：Web 控制器，负责接收 HTTP 请求、调用领域服务并构造统一响应。 */
class OnlineUserController(
    private val onlinePresenceService: OnlinePresenceService,
    private val userRepository: UserRepository,
    private val responseBuilder: ResponseBuilder,
) {
    
    @GetMapping
    @PreAuthorize("hasAuthority('user:read')")
            /** list：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun list(): ResponseEntity<Response> {
        data class UserData(
            val id: Long,
            val username: String,
            @param:JsonProperty("display_name") val displayName: String?,
            val role: String,
            @param:JsonProperty("last_seen") val lastSeen: LocalDateTime,
        )
        
        data class Response(
            val items: List<UserData>,
            val total: Int,
            @param:JsonProperty("captured_at") val capturedAt: LocalDateTime,
            @param:JsonProperty("stale_after_seconds") val staleAfterSeconds: Long,
        )
        
        val presences = onlinePresenceService.onlineUsers()
        val usersById = userRepository.findAllById(presences.map { it.userId }).associateBy { it.id }
        val items = presences.mapNotNull { presence ->
            val user = usersById[presence.userId] ?: return@mapNotNull null
            if (!user.enabled || user.status != User.Status.Activity) return@mapNotNull null
            UserData(user.id!!, user.username, user.nickName, user.role, presence.lastSeen)
        }
        val rs = Response(items, items.size, LocalDateTime.now(), OnlinePresenceService.STALE_AFTER_SECONDS)
        return responseBuilder.ok().data(rs).build()
    }
}
