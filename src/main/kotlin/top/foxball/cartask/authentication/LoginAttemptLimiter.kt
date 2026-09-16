package top.foxball.cartask.authentication

/**
 * LoginAttemptLimiter：认证子系统中的组件，负责实现相关安全、令牌或访问控制能力。
 *
 * 该文件中的类型和函数用于支撑登录认证流程，并在边界处校验输入与会话状态。
 */

/**
 * LoginAttemptLimiter 认证组件说明。
 *
 * 该文件集中定义认证流程所需的领域类型、服务及基础设施适配逻辑。
 */

import org.springframework.dao.DataAccessException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.*
import kotlin.math.ceil


@Component
/**
 * LoginAttemptLimiter 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
class LoginAttemptLimiter(
    private val redisTemplate: StringRedisTemplate,
    private val properties: LoginRateLimitProperties,
) {
    private val checkScript = DefaultRedisScript<Long>().apply {
        setScriptText(
            """
            local attempts = tonumber(redis.call('GET', KEYS[1]) or '0')
            if attempts < tonumber(ARGV[1]) then return 0 end
            local ttl = redis.call('PTTL', KEYS[1])
            if ttl <= 0 then
                redis.call('DEL', KEYS[1])
                return 0
            end
            return ttl
        """.trimIndent()
        )
        resultType = Long::class.java
    }
    private val recordFailureScript = DefaultRedisScript<Long>().apply {
        setScriptText(
            """
            local attempts = redis.call('INCR', KEYS[1])
            if attempts == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[2]) end
            if attempts <= tonumber(ARGV[1]) then return 0 end
            local ttl = redis.call('PTTL', KEYS[1])
            if ttl <= 0 then
                redis.call('PEXPIRE', KEYS[1], ARGV[2])
                return tonumber(ARGV[2])
            end
            return ttl
        """.trimIndent()
        )
        resultType = Long::class.java
    }
    
    init {
        properties.validate()
    }
    
    
    /**
     * check 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    /** check：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
    fun check(username: String) {
        if (!properties.enabled) return
        guarded("检查登录失败次数") {
            val remainingMillis = redisTemplate.execute(
                checkScript,
                listOf(key(username)),
                properties.maxAttempts.toString(),
            ) ?: throw AuthenticationInfrastructureException("Redis 登录限流检查无响应")
            if (remainingMillis > 0) throw LoginRateLimitException(toSeconds(remainingMillis))
        }
    }
    
    
    /**
     * recordFailure 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    /** recordFailure：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
    fun recordFailure(username: String) {
        if (!properties.enabled) return
        guarded("记录登录失败") {
            val remainingMillis = redisTemplate.execute(
                recordFailureScript,
                listOf(key(username)),
                properties.maxAttempts.toString(),
                properties.window.toMillis().toString(),
            ) ?: throw AuthenticationInfrastructureException("Redis 登录限流记录无响应")
            if (remainingMillis > 0) throw LoginRateLimitException(toSeconds(remainingMillis))
        }
    }
    
    
    /**
     * clear 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    /** clear：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
    fun clear(username: String) {
        if (!properties.enabled) return
        guarded("清除登录失败次数") {
            redisTemplate.delete(key(username))
        }
    }
    
    
    /** key：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
    private fun key(username: String): String {
        val normalized = username.trim().lowercase(Locale.ROOT)
        val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(StandardCharsets.UTF_8))
        return "shopmall:auth:login-failure:${Base64.getUrlEncoder().withoutPadding().encodeToString(digest)}"
    }
    
    
    /** toSeconds：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
    private fun toSeconds(milliseconds: Long): Long = ceil(milliseconds / 1000.0).toLong().coerceAtLeast(1)
    
    private fun <T> guarded(operation: String, action: () -> T): T = try {
        action()
    } catch (ex: LoginRateLimitException) {
        throw ex
    } catch (ex: AuthenticationInfrastructureException) {
        throw ex
    } catch (ex: DataAccessException) {
        throw AuthenticationInfrastructureException("Redis $operation 失败", ex)
    } catch (ex: RuntimeException) {
        throw AuthenticationInfrastructureException("Redis $operation 失败", ex)
    }
}


