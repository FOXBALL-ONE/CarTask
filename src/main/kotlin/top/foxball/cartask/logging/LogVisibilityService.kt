package top.foxball.cartask.logging

/**
 * LogVisibilityService 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


@Service
/**
 * LogVisibilityService 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
class LogVisibilityService(
    private val redisTemplate: StringRedisTemplate,
) {
    enum class Type { LOGIN, OPERATION }
    
    
    /**
     * visibleAfter 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun visibleAfter(type: Type): LocalDateTime? = redisTemplate.opsForValue()
        .get(key(type))
        ?.let { value -> runCatching { LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME) }.getOrNull() }
    
    
    /**
     * clear 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun clear(type: Type): LocalDateTime {
        val clearedAt = LocalDateTime.now()
        redisTemplate.opsForValue().set(key(type), clearedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
        return clearedAt
    }
    
    
    private fun key(type: Type) = "shopmall:logs:visible-after:${type.name.lowercase()}"
}


