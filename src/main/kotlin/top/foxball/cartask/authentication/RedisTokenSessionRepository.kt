package top.foxball.cartask.authentication

/**
 * RedisTokenSessionRepository：认证子系统中的组件，负责实现相关安全、令牌或访问控制能力。
 *
 * 该文件中的类型和函数用于支撑登录认证流程，并在边界处校验输入与会话状态。
 */

/**
 * RedisTokenSessionRepository 认证组件说明。
 *
 * 该文件集中定义认证流程所需的领域类型、服务及基础设施适配逻辑。
 */

import org.springframework.dao.DataAccessException
import org.springframework.data.redis.core.RedisOperations
import org.springframework.data.redis.core.SessionCallback
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Repository
import tools.jackson.databind.ObjectMapper
import java.time.Duration


@Repository
/**
 * RedisTokenSessionRepository 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
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
     * currentTokenVersion 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    /** currentTokenVersion：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
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
     * save 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    /** save：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
    fun save(tokenId: String, session: RedisTokenSession, ttl: Duration) = guarded("保存 JWT 会话") {
        if (ttl.isZero || ttl.isNegative) throw JwtAuthenticationException("登录凭据已过期")
        val wasSaved = redisTemplate.opsForValue().setIfAbsent(
            sessionKey(tokenId), objectMapper.writeValueAsString(session), ttl,
        )
        if (wasSaved != true) throw AuthenticationInfrastructureException("JWT 会话 ID 冲突")
    }
    
    
    /**
     * validate 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    /** validate：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
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
     * delete 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    /** delete：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
    fun delete(tokenId: String) = guarded("删除 JWT 会话") {
        redisTemplate.delete(sessionKey(tokenId))
    }
    
    
    /**
     * updateWorkingDepartment 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    /** updateWorkingDepartment：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
    fun updateWorkingDepartment(tokenId: String, departmentId: Long?): Boolean = guarded("更新 JWT 会话工作部门") {
        val key = sessionKey(tokenId)
        repeat(SESSION_UPDATE_ATTEMPTS) {
            when (val updated = replaceWorkingDepartment(key, departmentId)) {
                null -> Unit
                else -> return@guarded updated
            }
        }
        throw AuthenticationInfrastructureException("并发更新 JWT 会话工作部门失败")
    }
    
    
    /** replaceWorkingDepartment：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
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
                return if (ops.exec() == null) null else true
            }
        })
    
    
    /**
     * incrementTokenVersion 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    /** incrementTokenVersion：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
    fun incrementTokenVersion(userId: Long): Long = guarded("撤销用户 JWT 会话") {
        redisTemplate.opsForValue().increment(tokenVersionKey(userId))
            ?: throw AuthenticationInfrastructureException("Redis token version 递增失败")
    }
    
    
    /** readSession：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
    private fun readSession(text: String): RedisTokenSession = try {
        objectMapper.readValue(text, RedisTokenSession::class.java)
    } catch (ex: Exception) {
        throw JwtAuthenticationException("登录状态损坏", ex)
    }
    
    
    /** sessionKey：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
    private fun sessionKey(tokenId: String) = "shopmall:auth:jwt:$tokenId"
    
    
    /** tokenVersionKey：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
    private fun tokenVersionKey(userId: Long) = "shopmall:auth:user:$userId:token-version"
    
    private companion object {
        
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


