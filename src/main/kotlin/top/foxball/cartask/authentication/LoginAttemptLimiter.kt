package top.foxball.cartask.authentication

import org.springframework.dao.DataAccessException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.*
import kotlin.math.ceil

/** 使用 Redis 对规范化用户名进行失败窗口计数，避免单实例内存限流被绕过。 */
@Component
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
     * check：校验输入、状态或访问条件。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param username 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
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
     * recordFailure：执行当前模块中的业务操作。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param username 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
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
     * clear：删除、清理或撤销相关数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param username 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun clear(username: String) {
        if (!properties.enabled) return
        guarded("清除登录失败次数") {
            redisTemplate.delete(key(username))
        }
    }

    /**
     * key：执行当前模块中的业务操作。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param username 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    private fun key(username: String): String {
        val normalized = username.trim().lowercase(Locale.ROOT)
        val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(StandardCharsets.UTF_8))
        return "shopmall:auth:login-failure:${Base64.getUrlEncoder().withoutPadding().encodeToString(digest)}"
    }

    /**
     * toSeconds：转换、构建或格式化数据。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param milliseconds 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
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
