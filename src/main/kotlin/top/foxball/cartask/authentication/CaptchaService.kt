package top.foxball.cartask.authentication

/**
 * CaptchaService：认证子系统中的组件，负责实现相关安全、令牌或访问控制能力。
 *
 * 该文件中的类型和函数用于支撑登录认证流程，并在边界处校验输入与会话状态。
 */

/**
 * CaptchaService 认证组件说明。
 *
 * 该文件集中定义认证流程所需的领域类型、服务及基础设施适配逻辑。
 */

import org.springframework.dao.DataAccessException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*


@Component
/**
 * CaptchaService 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
class CaptchaService(
    private val redisTemplate: StringRedisTemplate,
) {
    /**
     * CaptchaImage 的职责与行为说明。
     * 该声明负责认证域中的相关数据处理、校验或服务调用。
     */
    data class CaptchaImage(
        val token: String,
        val image: String,
    )
    
    private val random = SecureRandom()
    
    
    /**
     * generate 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    /** generate：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
    fun generate(): CaptchaImage = guarded("生成验证码") {
        val code = (CAPTCHA_MIN + random.nextInt(CAPTCHA_RANGE)).toString()
        val token = UUID.randomUUID().toString().replace("-", "")
        val stored = redisTemplate.execute(
            generateScript,
            listOf(key(token), ACTIVE_INDEX_KEY),
            "${hash(code)}|0",
            EXPIRE_MILLIS.toString(),
            MAX_ACTIVE_ENTRIES.toString(),
            System.currentTimeMillis().toString(),
            token,
        ) ?: throw AuthenticationInfrastructureException("Redis 验证码生成无响应")
        if (stored != RESULT_STORED) {
            throw AuthenticationInfrastructureException("验证码存储已达上限")
        }
        val svg = render(code)
        val image = "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(svg.toByteArray())
        CaptchaImage(token, image)
    }
    
    /**
     * verify 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    /** verify：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
    fun verify(token: String?, answer: String?) = guarded("校验验证码") {
        if (token.isNullOrBlank() || answer.isNullOrBlank()) {
            throw BadCredentialsException("请输入验证码")
        }
        val result = redisTemplate.execute(
            verifyScript,
            listOf(key(token), ACTIVE_INDEX_KEY),
            hash(answer.trim()),
            MAX_VERIFY_ATTEMPTS.toString(),
        ) ?: throw AuthenticationInfrastructureException("Redis 验证码校验无响应")
        when (result) {
            RESULT_VALID -> Unit
            RESULT_MISSING -> throw BadCredentialsException("验证码已过期，请刷新")
            RESULT_INVALID -> throw BadCredentialsException("验证码错误")
            RESULT_EXHAUSTED -> throw BadCredentialsException("验证码错误次数过多，请刷新")
            else -> throw AuthenticationInfrastructureException("Redis 验证码校验返回异常")
        }
    }
    
    
    /** hash：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
    private fun hash(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }
    
    
    /** key：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
    private fun key(token: String) = "$KEY_PREFIX$token"
    
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
    
    
    /** render：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
    private fun render(code: String): String {
        val lines = buildString {
            val lineCount = 8 + random.nextInt(5)
            repeat(lineCount) {
                val x1 = random.nextDouble() * WIDTH
                val y1 = random.nextDouble() * HEIGHT
                val x2 = random.nextDouble() * WIDTH
                val y2 = random.nextDouble() * HEIGHT
                val color =
                    "rgba(${random.nextDouble() * 150}, ${random.nextDouble() * 150}, ${random.nextDouble() * 150}, 0.3)"
                val strokeWidth = random.nextDouble() * 2 + 0.5
                append("""<line x1="$x1" y1="$y1" x2="$x2" y2="$y2" stroke="$color" stroke-width="$strokeWidth"/>""")
            }
        }
        
        val dots = buildString {
            val dotCount = 50 + random.nextInt(31)
            repeat(dotCount) {
                val cx = random.nextDouble() * WIDTH
                val cy = random.nextDouble() * HEIGHT
                val r = random.nextDouble() * 2 + 1
                val color =
                    "rgba(${random.nextDouble() * 200}, ${random.nextDouble() * 200}, ${random.nextDouble() * 200}, 0.4)"
                append("""<circle cx="$cx" cy="$cy" r="$r" fill="$color"/>""")
            }
        }
        
        val chars = buildString {
            val charWidth = WIDTH.toDouble() / code.length
            code.forEachIndexed { index, char ->
                val x = charWidth * index + charWidth / 2 + (random.nextDouble() * 8 - 4)
                val y = HEIGHT / 2.0 + (random.nextDouble() * 8 - 4)
                val fontSize = random.nextDouble() * 6 + 20
                val angle = random.nextDouble() * 40 - 20
                val color = COLORS[random.nextInt(COLORS.size)]
                append(
                    """<text x="$x" y="$y" font-size="$fontSize" font-weight="bold" fill="$color" """ +
                            """text-anchor="middle" dominant-baseline="middle" transform="rotate($angle $x $y)">$char</text>""",
                )
            }
        }
        
        return """<svg width="$WIDTH" height="$HEIGHT" xmlns="http://www.w3.org/2000/svg">
    <defs>
      <linearGradient id="bg" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" style="stop-color:#e0e7ff;stop-opacity:1" />
        <stop offset="100%" style="stop-color:#f3e8ff;stop-opacity:1" />
      </linearGradient>
    </defs>
    <rect width="$WIDTH" height="$HEIGHT" fill="url(#bg)"/>
    $lines
    $dots
    $chars
  </svg>"""
    }
    
    private companion object {
        const val EXPIRE_MILLIS = 5 * 60 * 1000L
        const val CAPTCHA_MIN = 1000
        const val CAPTCHA_RANGE = 9000
        const val WIDTH = 120
        const val HEIGHT = 44
        
        const val MAX_VERIFY_ATTEMPTS = 5
        
        const val KEY_PREFIX = "shopmall:auth:captcha:"
        const val ACTIVE_INDEX_KEY = "${KEY_PREFIX}index"
        const val MAX_ACTIVE_ENTRIES = 10_000
        
        const val RESULT_STORED = 1L
        const val RESULT_MISSING = 0L
        const val RESULT_VALID = 1L
        const val RESULT_INVALID = -1L
        const val RESULT_EXHAUSTED = -2L
        
        val generateScript = DefaultRedisScript<Long>().apply {
            setScriptText(
                """
                redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', ARGV[4])
                if redis.call('ZCARD', KEYS[2]) >= tonumber(ARGV[3]) then
                    return 0
                end
                redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2])
                redis.call('ZADD', KEYS[2], tonumber(ARGV[4]) + tonumber(ARGV[2]), ARGV[5])
                redis.call('PEXPIRE', KEYS[2], ARGV[2])
                return 1
                """.trimIndent(),
            )
            resultType = Long::class.java
        }
        
        val verifyScript = DefaultRedisScript<Long>().apply {
            setScriptText(
                """
                local value = redis.call('GET', KEYS[1])
                if not value then
                    redis.call('ZREM', KEYS[2], string.sub(KEYS[1], string.len('shopmall:auth:captcha:') + 1))
                    return 0
                end

                local separator = string.find(value, '|', 1, true)
                if not separator then
                    redis.call('DEL', KEYS[1])
                    redis.call('ZREM', KEYS[2], string.sub(KEYS[1], string.len('shopmall:auth:captcha:') + 1))
                    return 0
                end

                local stored_hash = string.sub(value, 1, separator - 1)
                local failed_attempts = tonumber(string.sub(value, separator + 1))
                if not failed_attempts then
                    redis.call('DEL', KEYS[1])
                    redis.call('ZREM', KEYS[2], string.sub(KEYS[1], string.len('shopmall:auth:captcha:') + 1))
                    return 0
                end

                if stored_hash == ARGV[1] then
                    redis.call('DEL', KEYS[1])
                    redis.call('ZREM', KEYS[2], string.sub(KEYS[1], string.len('shopmall:auth:captcha:') + 1))
                    return 1
                end

                failed_attempts = failed_attempts + 1
                if failed_attempts >= tonumber(ARGV[2]) then
                    redis.call('DEL', KEYS[1])
                    redis.call('ZREM', KEYS[2], string.sub(KEYS[1], string.len('shopmall:auth:captcha:') + 1))
                    return -2
                end

                local ttl = redis.call('PTTL', KEYS[1])
                if ttl <= 0 then
                    redis.call('DEL', KEYS[1])
                    redis.call('ZREM', KEYS[2], string.sub(KEYS[1], string.len('shopmall:auth:captcha:') + 1))
                    return 0
                end
                redis.call('SET', KEYS[1], stored_hash .. '|' .. failed_attempts, 'PX', ttl)
                return -1
                """.trimIndent(),
            )
            resultType = Long::class.java
        }
        
        val COLORS = listOf("#2563eb", "#dc2626", "#059669", "#7c3aed", "#ea580c", "#0891b2")
    }
}


