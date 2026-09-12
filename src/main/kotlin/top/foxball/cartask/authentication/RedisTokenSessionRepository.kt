package top.foxball.cartask.authentication

import tools.jackson.databind.ObjectMapper
import org.springframework.dao.DataAccessException
import org.springframework.data.redis.core.RedisOperations
import org.springframework.data.redis.core.SessionCallback
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Repository
import java.time.Duration
import java.time.LocalDateTime

/** Redis 是 JWT 有效会话的唯一在线状态来源。 */
@Repository
class RedisTokenSessionRepository(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) {
    private val validateScript = DefaultRedisScript<List<*>>().apply {
        setScriptText(
            """
            local session = redis.call('GET', KEYS[1])
            if not session then return {'SESSION_MISSING'} end
            local version = redis.call('GET', KEYS[2])
            if not version then return {'VERSION_MISSING'} end
            return {'VALID', session, version}
        """.trimIndent()
        )
        resultType = List::class.java
    }
    
    fun currentTokenVersion(userId: Long): Long = guarded("读取用户 token version") {
        val key = tokenVersionKey(userId)
        val existing = redisTemplate.opsForValue().get(key)
        if (existing != null) return@guarded existing.toLongOrNull()
            ?: throw AuthenticationInfrastructureException("Redis token version 格式错误")
        
        redisTemplate.opsForValue().setIfAbsent(key, "0")
        redisTemplate.opsForValue().get(key)?.toLongOrNull()
            ?: throw AuthenticationInfrastructureException("Redis token version 初始化失败")
    }
    
    fun save(tokenId: String, session: RedisTokenSession, ttl: Duration) = guarded("保存 JWT 会话") {
        if (ttl.isZero || ttl.isNegative) throw JwtAuthenticationException("登录凭据已过期")
        val wasSaved = redisTemplate.opsForValue().setIfAbsent(
            sessionKey(tokenId), objectMapper.writeValueAsString(session), ttl,
        )
        if (wasSaved != true) throw AuthenticationInfrastructureException("JWT 会话 ID 冲突")
    }
    
    fun validate(tokenId: String, userId: Long): RedisTokenSession = guarded("校验 JWT 会话") {
        val result = redisTemplate.execute(
            validateScript,
            listOf(sessionKey(tokenId), tokenVersionKey(userId)),
        ) ?: throw AuthenticationInfrastructureException("Redis JWT 会话校验无响应")
        when (result.firstOrNull()?.toString()) {
            "SESSION_MISSING" -> throw JwtAuthenticationException("登录状态不存在或已失效")
            "VERSION_MISSING" -> throw JwtAuthenticationException("登录状态无效")
            "VALID" -> Unit
            else -> throw AuthenticationInfrastructureException("Redis JWT 会话校验返回异常")
        }
        val sessionText = result.getOrNull(1)?.toString() ?: throw JwtAuthenticationException("登录状态无效")
        val version = result.getOrNull(2)?.toString()?.toLongOrNull()
            ?: throw JwtAuthenticationException("登录状态无效")
        val session = readSession(sessionText)
        if (session.tokenVersion != version) throw JwtAuthenticationException("登录状态已撤销")
        session
    }
    
    fun delete(tokenId: String) = guarded("删除 JWT 会话") {
        redisTemplate.delete(sessionKey(tokenId))
    }

    /**
     * 就地更新会话的当前工作部门；会话不存在或已过期时返回 false，绝不复活会话。
     *
     * 用 WATCH/MULTI/EXEC 而不是 Lua 重新编码：会话正文里是 AES-GCM 密文和布尔值，
     * 用 cjson 重编码整个对象有静默损坏的风险，而这里复用同一个 ObjectMapper 与同一个
     * [RedisTokenSession]，序列化形状与 [save] 完全一致。TTL 由会话自带的 expiresAt 重算，
     * 不依赖 Redis 6 的 KEEPTTL。
     */
    fun updateWorkingDepartment(tokenId: String, departmentId: Long?): Boolean = guarded("更新 JWT 会话工作部门") {
        val key = sessionKey(tokenId)
        repeat(SESSION_UPDATE_ATTEMPTS) {
            when (val updated = replaceWorkingDepartment(key, departmentId)) {
                // WATCH 命中并发修改，重试；其余情况（含会话不存在）直接返回。
                null -> Unit
                else -> return@guarded updated
            }
        }
        throw AuthenticationInfrastructureException("并发更新 JWT 会话工作部门失败")
    }

    /** 单次 WATCH 事务；返回 null 表示该会话在事务期间被并发修改，调用方应重试。 */
    private fun replaceWorkingDepartment(key: String, departmentId: Long?): Boolean? =
        redisTemplate.execute(object : SessionCallback<Boolean?> {
            override fun <K : Any, V : Any> execute(operations: RedisOperations<K, V>): Boolean? {
                @Suppress("UNCHECKED_CAST")
                val ops = operations as RedisOperations<String, String>
                ops.watch(key)
                val sessionText = ops.opsForValue().get(key)
                if (sessionText == null) {
                    ops.unwatch()
                    return false
                }
                val session = readSession(sessionText)
                val ttl = Duration.between(LocalDateTime.now(), session.expiresAt)
                if (ttl.isZero || ttl.isNegative) {
                    ops.unwatch()
                    return false
                }
                ops.multi()
                ops.opsForValue().set(
                    key,
                    objectMapper.writeValueAsString(session.copy(workingDepartmentId = departmentId)),
                    ttl,
                )
                // EXEC 返回 null 表示事务因 WATCH 被放弃（并发修改），需要重试。
                return if (ops.exec() == null) null else true
            }
        })

    /** 先撤销，再修改凭据、角色或账户状态。 */
    fun incrementTokenVersion(userId: Long): Long = guarded("撤销用户 JWT 会话") {
        redisTemplate.opsForValue().increment(tokenVersionKey(userId))
            ?: throw AuthenticationInfrastructureException("Redis token version 递增失败")
    }
    
    private fun readSession(text: String): RedisTokenSession = try {
        objectMapper.readValue(text, RedisTokenSession::class.java)
    } catch (ex: Exception) {
        throw JwtAuthenticationException("登录状态损坏", ex)
    }
    
    private fun sessionKey(tokenId: String) = "shopmall:auth:jwt:$tokenId"

    private fun tokenVersionKey(userId: Long) = "shopmall:auth:user:$userId:token-version"

    private companion object {
        /** WATCH 事务被并发修改打断时的重试次数。 */
        const val SESSION_UPDATE_ATTEMPTS = 3
    }

    private fun <T> guarded(operation: String, action: () -> T): T = try {
        action()
    } catch (ex: JwtAuthenticationException) {
        throw ex
    } catch (ex: AuthenticationInfrastructureException) {
        throw ex
    } catch (ex: DataAccessException) {
        throw AuthenticationInfrastructureException("Redis $operation 失败", ex)
    } catch (ex: RuntimeException) {
        throw AuthenticationInfrastructureException("Redis $operation 失败", ex)
    }
}
