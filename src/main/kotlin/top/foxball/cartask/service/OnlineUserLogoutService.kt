package top.foxball.cartask.service

import org.springframework.stereotype.Service
import top.foxball.cartask.audit.AuditAction
import top.foxball.cartask.audit.AuditCommand
import top.foxball.cartask.audit.AuditService
import top.foxball.cartask.authentication.RedisTokenSessionRepository
import top.foxball.cartask.repository.UserRepository

/**
 * 把在线用户踢下线。
 *
 * 撤销动作落在 Redis 的 token version 上：每个用户都有一个版本号，签发 JWT 时把这个号写进会话，
 * 之后每次鉴权都由 [RedisTokenSessionRepository.validate] 拿会话里的版本号和当前版本比对，
 * 版本一涨，该用户**所有已签发**的 token 立刻失效——不需要、也没法按用户去枚举 tokenId，
 * 因为会话键是按 tokenId 存的，没有反向索引。所以"清理登录凭据"就是自增版本号这一个动作。
 *
 * 只授予超级管理员（见 PermissionCatalogInitializer 的 SUPER_ADMIN_ONLY_PERMISSION_CODES）：
 * 这是能把人踢出系统的操作，与改密、改角色同级。
 */
@Service
class OnlineUserLogoutService(
    private val userRepository: UserRepository,
    private val tokenSessionRepository: RedisTokenSessionRepository,
    private val onlinePresenceService: OnlinePresenceService,
    private val auditService: AuditService,
) {

    /**
     * 一次强制登出的结果。
     *
     * skipped 是查不到的用户 id（例如提交前刚好被删除）：已经不存在的人没什么可撤销的，
     * 报错会让整批操作白做，所以单独列出来让前端提示。
     */
    data class Result(
        val loggedOut: List<Long>,
        val skipped: List<Long>,
    )

    fun forceLogout(userIds: Collection<Long>): Result {
        val targets = userIds.filter { it > 0 }.distinct()
        require(targets.isNotEmpty()) { "请至少选择一个用户" }
        require(targets.size <= MAX_TARGETS) { "一次最多登出 $MAX_TARGETS 个用户" }

        val users = userRepository.findAllById(targets).associateBy { it.id }
        val loggedOut = mutableListOf<Long>()
        val skipped = mutableListOf<Long>()

        targets.forEach { userId ->
            val user = users[userId]
            if (user == null) {
                skipped.add(userId)
                return@forEach
            }
            // 先撤会话再清在线标记：反过来的话，撤销失败会留下"名单里已经没他、但他还能调接口"的假象。
            tokenSessionRepository.incrementTokenVersion(userId)
            onlinePresenceService.remove(listOf(userId))
            loggedOut.add(userId)
            recordAudit(userId, user.username)
        }
        return Result(loggedOut, skipped)
    }

    /**
     * 审计写失败不回滚登出。
     *
     * 与 AuthServiceImpl 里登录失败的审计同样处理：踢人是安全动作，已经生效就不该因为
     * 记流水失败而回退，也不该让调用方以为没踢成功。
     */
    private fun recordAudit(userId: Long, username: String) {
        runCatching {
            auditService.record(
                AuditCommand(
                    AuditAction.AUTH_FORCED_LOGOUT,
                    "user",
                    targetId = username,
                    reasonCode = "FORCED_BY_ADMIN",
                    targetSummary = mapOf("user_id" to userId, "username" to username),
                ),
            )
        }
    }

    private companion object {
        /** 在线名册一次也就几十个人，这个上限只是挡住构造超大请求体的情况。 */
        const val MAX_TARGETS = 200
    }
}
