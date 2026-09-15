package top.foxball.cartask.service

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 记录最近一次已认证请求，用于实时在线用户视图。
 * Redis 有序集合只保存短期心跳，不产生数据库数据，也不需要迁移。
 */
@Service
class OnlinePresenceService(
    private val redisTemplate: StringRedisTemplate,
) {
    companion object {
        private const val PRESENCE_KEY = "shopmall:presence:users"
        const val STALE_AFTER_SECONDS = 10L
    }

    /**
     * touch：更新业务状态或修改相关配置。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param userId 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun touch(userId: Long) {
        redisTemplate.opsForZSet().add(PRESENCE_KEY, userId.toString(), System.currentTimeMillis().toDouble())
    }

    /**
     * onlineUsers：处理请求、事件或异常流程。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun onlineUsers(): List<Presence> {
        val cutoff = System.currentTimeMillis() - STALE_AFTER_SECONDS * 1_000
        redisTemplate.opsForZSet().removeRangeByScore(PRESENCE_KEY, Double.NEGATIVE_INFINITY, cutoff.toDouble())
        return redisTemplate.opsForZSet()
            .rangeByScoreWithScores(PRESENCE_KEY, cutoff.toDouble(), Double.POSITIVE_INFINITY)
            .orEmpty()
            .mapNotNull { entry ->
                val userId = entry.value?.toLongOrNull() ?: return@mapNotNull null
                val score = entry.score ?: return@mapNotNull null
                Presence(
                    userId = userId,
                    lastSeen = LocalDateTime.ofInstant(Instant.ofEpochMilli(score.toLong()), ZoneId.systemDefault()),
                )
            }
            .sortedByDescending { it.lastSeen }
    }

    data class Presence(
        val userId: Long,
        val lastSeen: LocalDateTime,
    )
}
