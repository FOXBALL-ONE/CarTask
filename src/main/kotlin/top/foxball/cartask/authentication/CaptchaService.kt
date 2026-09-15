package top.foxball.cartask.authentication

import org.springframework.dao.DataAccessException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*

/**
 * 登录图形验证码：4 位数字 + SVG 干扰线/干扰点，Redis 存储、5 分钟过期、一次性使用。
 * 与原型 20260625115857/server.js 的 /api/captcha 生成算法和行为保持一致。
 */
@Component
class CaptchaService(
    private val redisTemplate: StringRedisTemplate,
) {
    data class CaptchaImage(
        val token: String,
        val image: String,
    )

    private val random = SecureRandom()

    /**
     * generate：执行当前模块中的业务操作。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
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

    /** 校验失败抛 [BadCredentialsException]，由全局异常处理统一转换为 401 + 提示消息。 */
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

    /**
     * hash：查询或读取相关数据。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param value 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    private fun hash(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    /**
     * key：执行当前模块中的业务操作。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param token 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
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

    /**
     * render：转换、构建或格式化数据。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param code 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    private fun render(code: String): String {
        // 干扰线
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

        // 干扰点
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

        // 文字：每个字符不同颜色和角度
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

        /** 同一验证码允许答错的上限，超过即作废。 */
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
