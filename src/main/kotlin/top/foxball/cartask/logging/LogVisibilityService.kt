package top.foxball.cartask.logging

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** 在不破坏审计哈希链的前提下，为管理端日志列表保存全局清空时间点。 */
@Service
class LogVisibilityService(
    private val redisTemplate: StringRedisTemplate,
) {
    enum class Type { LOGIN, OPERATION }

    /**
     * visibleAfter：执行当前模块中的业务操作。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param type 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun visibleAfter(type: Type): LocalDateTime? = redisTemplate.opsForValue()
        .get(key(type))
        ?.let { value -> runCatching { LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME) }.getOrNull() }

    /**
     * clear：删除、清理或撤销相关数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param type 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun clear(type: Type): LocalDateTime {
        val clearedAt = LocalDateTime.now()
        redisTemplate.opsForValue().set(key(type), clearedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
        return clearedAt
    }

    /**
     * key：执行当前模块中的业务操作。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param type 参与本次处理的输入参数。
     * @param shopmall 参与本次处理的输入参数。
     * @param logs 参与本次处理的输入参数。
     * @param after 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    private fun key(type: Type) = "shopmall:logs:visible-after:${type.name.lowercase()}"
}
