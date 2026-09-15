package top.foxball.cartask.authentication

import org.springframework.dao.DataAccessException
import org.springframework.data.redis.core.RedisOperations
import org.springframework.data.redis.core.SessionCallback
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Repository
import tools.jackson.databind.ObjectMapper
import java.time.Duration

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

    /**
     * currentTokenVersion：查询或读取相关数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param userId 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun currentTokenVersion(userId: Long): Long = guarded("读取用户 token version") {
        val key = tokenVersionKey(userId)
        val existing = redisTemplate.opsForValue().get(key)
        if (existing != null) return@guarded existing.toLongOrNull()
            ?: throw AuthenticationInfrastructureException("Redis token version 格式错误")

        redisTemplate.opsForValue().setIfAbsent(key, "0")
        redisTemplate.opsForValue().get(key)?.toLongOrNull()
            ?: throw AuthenticationInfrastructureException("Redis token version 初始化失败")
    }

    /**
     * save：创建、保存或初始化相关数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param tokenId 参与本次处理的输入参数。
     * @param session 参与本次处理的输入参数。
     * @param ttl 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun save(tokenId: String, session: RedisTokenSession, ttl: Duration) = guarded("保存 JWT 会话") {
        if (ttl.isZero || ttl.isNegative) throw JwtAuthenticationException("登录凭据已过期")
        val wasSaved = redisTemplate.opsForValue().setIfAbsent(
            sessionKey(tokenId), objectMapper.writeValueAsString(session), ttl,
        )
        if (wasSaved != true) throw AuthenticationInfrastructureException("JWT 会话 ID 冲突")
    }

    /**
     * validate：校验输入、状态或访问条件。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param tokenId 参与本次处理的输入参数。
     * @param userId 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
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

    /**
     * delete：删除、清理或撤销相关数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param tokenId 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun delete(tokenId: String) = guarded("删除 JWT 会话") {
        redisTemplate.delete(sessionKey(tokenId))
    }

    /**
     * 就地更新会话的当前工作部门；会话不存在或已过期时返回 false，绝不复活会话。
     *
     * 用 WATCH/MULTI/EXEC 而不是 Lua 重新编码：会话正文里是 AES-GCM 密文和布尔值，
     * 用 cjson 重编码整个对象有静默损坏的风险，而这里复用同一个 ObjectMapper 与同一个
     * [RedisTokenSession]，序列化形状与 [save] 完全一致。TTL 取 key 当前的剩余有效期，
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
                // 剩余有效期以 Redis key 自己的 TTL 为准，而不是拿会话里的 expiresAt 和本机时钟相减：
                // expiresAt 是按 UTC 写入的（见 JwtTokenService），在 UTC+8 的主机上相减恒为负数，
                // 每次更新都会被误判成「会话已过期」而静默跳过——工作部门切换就是这样一直是坏的。
                // Redis 的 TTL 没有时区口径问题，且与 key 上真实的过期时刻严格一致。
                val remainingSeconds = ops.getExpire(key)
                if (remainingSeconds == null || remainingSeconds <= 0) {
                    ops.unwatch()
                    return false
                }
                val ttl = Duration.ofSeconds(remainingSeconds)
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

    /**
     * readSession：查询或读取相关数据。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param text 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    private fun readSession(text: String): RedisTokenSession = try {
        objectMapper.readValue(text, RedisTokenSession::class.java)
    } catch (ex: Exception) {
        throw JwtAuthenticationException("登录状态损坏", ex)
    }

    /**
     * sessionKey：执行当前模块中的业务操作。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param tokenId 参与本次处理的输入参数。
     * @param shopmall 参与本次处理的输入参数。
     * @param auth 参与本次处理的输入参数。
     * @param jwt 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    private fun sessionKey(tokenId: String) = "shopmall:auth:jwt:$tokenId"

    /**
     * tokenVersionKey：完成身份认证、令牌或验证码处理。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param userId 参与本次处理的输入参数。
     * @param shopmall 参与本次处理的输入参数。
     * @param auth 参与本次处理的输入参数。
     * @param user 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
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
