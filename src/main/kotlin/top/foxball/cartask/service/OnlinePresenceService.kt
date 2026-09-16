package top.foxball.cartask.service

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId


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


    /**
     * 把用户从在线集合里摘掉。
     *
     * 只动这份「最近有请求」的名单：真正让人下线的是撤销会话（token version 自增），
     * 这里只是让名单立刻少人，否则要等满 STALE_AFTER_SECONDS 心跳过期才消失。
     */
    fun remove(userIds: Collection<Long>) {
        if (userIds.isEmpty()) return
        redisTemplate.opsForZSet().remove(PRESENCE_KEY, *userIds.map(Long::toString).toTypedArray())
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
