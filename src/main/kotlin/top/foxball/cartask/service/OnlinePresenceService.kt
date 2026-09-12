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

    fun touch(userId: Long) {
        redisTemplate.opsForZSet().add(PRESENCE_KEY, userId.toString(), System.currentTimeMillis().toDouble())
    }

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
