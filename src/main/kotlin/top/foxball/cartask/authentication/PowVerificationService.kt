package top.foxball.cartask.authentication

import org.springframework.dao.DataAccessException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.util.Base64

@Component
class PowVerificationService(
    private val redisTemplate: StringRedisTemplate,
    private val clock: Clock,
) {
    data class Challenge(
        val challengeId: String,
        val purpose: String,
        val seed: String,
        val difficultyBits: Int,
        val expiresAt: LocalDateTime,
        val algorithm: String = "SHA-256",
        val protocolVersion: Int = PROTOCOL_VERSION,
    )

    private val random = SecureRandom()

    internal fun hasLeadingZeroBitsForTest(digest: ByteArray, difficultyBits: Int) =
        hasLeadingZeroBits(digest, difficultyBits)

    fun issue(purpose: String): Challenge = guarded("签发工作量挑战") {
        val normalizedPurpose = normalizePurpose(purpose)
        val challengeId = randomBytes(CHALLENGE_ID_BYTES)
        val seed = randomBytes(SEED_BYTES)
        val issuedAt = clock.instant().toEpochMilli()
        val expiresAt = issuedAt + CHALLENGE_TTL.toMillis()
        val difficulty = DEFAULT_DIFFICULTY_BITS
        val state = listOf(normalizedPurpose, seed, difficulty, expiresAt, POLICY_VERSION).joinToString("|")
        val saved = redisTemplate.opsForValue().setIfAbsent(key(challengeId), state, CHALLENGE_TTL)
        if (saved != true) throw AuthenticationInfrastructureException("Redis 工作量挑战 ID 冲突")
        Challenge(
            challengeId,
            normalizedPurpose,
            seed,
            difficulty,
            LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(expiresAt), clock.zone),
        )
    }

    fun verify(challengeId: String?, purpose: String?, nonce: String?) = guarded("校验工作量证明") {
        if (challengeId.isNullOrBlank() || challengeId.length > 64 ||
            purpose.isNullOrBlank() || nonce == null || !NONCE_PATTERN.matches(nonce)
        ) {
            throw BadCredentialsException(INVALID_PROOF_MESSAGE)
        }
        val normalizedPurpose = runCatching { normalizePurpose(purpose) }
            .getOrElse { throw BadCredentialsException(INVALID_PROOF_MESSAGE) }
        val challengeKey = key(challengeId)
        val state = redisTemplate.opsForValue().get(challengeKey)
            ?: throw BadCredentialsException(INVALID_PROOF_MESSAGE)
        val fields = state.split('|')
        if (fields.size != 5) {
            redisTemplate.delete(challengeKey)
            throw BadCredentialsException(INVALID_PROOF_MESSAGE)
        }
        val (storedPurpose, seed, difficultyRaw, expiresAtRaw) = fields
        val difficulty = difficultyRaw.toIntOrNull()
        val expiresAt = expiresAtRaw.toLongOrNull()
        if (storedPurpose != normalizedPurpose || difficulty == null || difficulty !in MIN_DIFFICULTY_BITS..MAX_DIFFICULTY_BITS ||
            expiresAt == null || clock.instant().toEpochMilli() >= expiresAt ||
            !hasLeadingZeroBits(digest(challengeId, normalizedPurpose, seed, nonce), difficulty)
        ) {
            throw BadCredentialsException(INVALID_PROOF_MESSAGE)
        }
        val consumed = redisTemplate.execute(
            consumeScript,
            listOf(challengeKey),
            state,
        ) ?: throw AuthenticationInfrastructureException("Redis 工作量挑战消费无响应")
        if (consumed != 1L) throw BadCredentialsException(INVALID_PROOF_MESSAGE)
    }

    /** 仅用于独立验证接口；业务端点应直接调用 verify，保证验证和业务校验在同一请求内完成。 */
    fun verifyForEndpoint(challengeId: String?, purpose: String?, nonce: String?) = verify(challengeId, purpose, nonce)

    private fun normalizePurpose(purpose: String): String {
        val normalized = purpose.trim().uppercase()
        if (normalized !in ALLOWED_PURPOSES) throw IllegalArgumentException("工作量挑战用途无效")
        return normalized
    }

    private fun digest(challengeId: String, purpose: String, seed: String, nonce: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(
            "$PROTOCOL_PREFIX$challengeId:$purpose:$seed:$nonce".toByteArray(StandardCharsets.UTF_8),
        )

    private fun hasLeadingZeroBits(digest: ByteArray, difficultyBits: Int): Boolean {
        val completeBytes = difficultyBits / 8
        for (index in 0 until completeBytes) if (digest[index].toInt() != 0) return false
        val remainingBits = difficultyBits % 8
        if (remainingBits == 0) return true
        val mask = (0xff shl (8 - remainingBits)) and 0xff
        return (digest[completeBytes].toInt() and 0xff and mask) == 0
    }

    private fun randomBytes(size: Int): String {
        val bytes = ByteArray(size)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun key(challengeId: String) = "$KEY_PREFIX$challengeId"

    private fun <T> guarded(operation: String, action: () -> T): T = try {
        action()
    } catch (ex: BadCredentialsException) {
        throw ex
    } catch (ex: AuthenticationInfrastructureException) {
        throw ex
    } catch (ex: DataAccessException) {
        throw AuthenticationInfrastructureException("Redis $operation 失败", ex)
    } catch (ex: RuntimeException) {
        throw AuthenticationInfrastructureException("Redis $operation 失败", ex)
    }

    private companion object {
        const val PROTOCOL_VERSION = 1
        const val POLICY_VERSION = "v1"
        const val PROTOCOL_PREFIX = "shopmall-pow:v1:"
        const val KEY_PREFIX = "shopmall:auth:pow:v1:challenge:"
        const val CHALLENGE_ID_BYTES = 24
        const val SEED_BYTES = 32
        const val DEFAULT_DIFFICULTY_BITS = 16
        const val MIN_DIFFICULTY_BITS = 8
        const val MAX_DIFFICULTY_BITS = 24
        const val INVALID_PROOF_MESSAGE = "人机验证未通过，请重试"
        val CHALLENGE_TTL: Duration = Duration.ofMinutes(2)
        val ALLOWED_PURPOSES = setOf("LOGIN", "SMS_SEND")
        val NONCE_PATTERN = Regex("(?:0|[1-9][0-9]{0,19})")

        val consumeScript = DefaultRedisScript<Long>().apply {
            setScriptText(
                """
                if redis.call('GET', KEYS[1]) == ARGV[1] then
                    redis.call('DEL', KEYS[1])
                    return 1
                end
                return 0
                """.trimIndent(),
            )
            resultType = Long::class.java
        }
    }
}
