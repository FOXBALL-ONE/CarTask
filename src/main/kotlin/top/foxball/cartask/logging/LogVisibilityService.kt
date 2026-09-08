package top.foxball.cartask.logging

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service

/** 在不破坏审计哈希链的前提下，为管理端日志列表保存全局清空时间点。 */
@Service
class LogVisibilityService(
    private val redisTemplate: StringRedisTemplate,
) {
    enum class Type { LOGIN, OPERATION }

    fun visibleAfter(type: Type): LocalDateTime? = redisTemplate.opsForValue()
        .get(key(type))
        ?.let { value -> runCatching { LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME) }.getOrNull() }

    fun clear(type: Type): LocalDateTime {
        val clearedAt = LocalDateTime.now()
        redisTemplate.opsForValue().set(key(type), clearedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
        return clearedAt
    }

    private fun key(type: Type) = "shopmall:logs:visible-after:${type.name.lowercase()}"
}
