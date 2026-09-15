package top.foxball.setup

import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import org.springframework.stereotype.Component
import java.time.Duration

data class RedisProbeResult(
    val version: String,
    val database: Int,
)

/**
 * Redis 连通性探测。
 *
 * 用 Lettuce 直接连，理由同 [DatabaseProbe]：配置模式下 Redis 的自动配置同样被排除了。
 *
 * 连的是 5 号库——认证时 JWT 会话的在线状态就存在那里，而库号是写死在 `application.yaml` 里的，
 * 不在向导里问。连 5 号库而不是 0 号，是为了让「服务端把库数限制成了 1」这类配置差异在这里就暴露，
 * 而不是等到实施人员登录时才发现登录不上去。
 */
@Component
class RedisProbe {

    /**
     * probe：执行数据同步、探测或文件处理。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param host 参与本次处理的输入参数。
     * @param port 参与本次处理的输入参数。
     * @param password 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun probe(host: String, port: Int, password: String): RedisProbeResult {
        if (host.isBlank()) throw SetupException("Redis 主机不能为空")
        if (port !in 1..65535) throw SetupException("Redis 端口必须在 1-65535 之间")

        val builder = RedisURI.Builder.redis(host.trim(), port)
            .withDatabase(DATABASE)
            .withTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
        if (password.isNotEmpty()) {
            builder.withPassword(password)
        }

        val version = try {
            RedisClient.create(builder.build()).use { client ->
                client.connect().use { connection ->
                    val commands = connection.sync()
                    commands.ping()
                    redisVersion(commands.info("server"))
                }
            }
        } catch (exception: Exception) {
            throw SetupException(describe(exception), exception)
        }
        return RedisProbeResult(version = version, database = DATABASE)
    }

    /**
     * redisVersion：执行当前模块中的业务操作。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param info 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    private fun redisVersion(info: String?): String =
        VERSION_PATTERN.find(info.orEmpty())?.groupValues?.get(1) ?: "未知"

    /**
     * describe：转换、构建或格式化数据。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param exception 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    private fun describe(exception: Exception): String {
        val raw = generateSequence(exception as Throwable) { it.cause }
            .mapNotNull { it.message }
            .joinToString("；")
        val hint = when {
            raw.contains("NOAUTH", ignoreCase = true) ||
                    raw.contains("WRONGPASS", ignoreCase = true) ||
                    raw.contains("invalid password", ignoreCase = true) -> "密码不正确"

            raw.contains("Connection refused", ignoreCase = true) ||
                    raw.contains("连接被拒绝", ignoreCase = true) -> "目标主机或端口上没有 Redis 在监听"

            raw.contains("Unknown host", ignoreCase = true) ||
                    raw.contains("Name or service not known", ignoreCase = true) -> "主机名无法解析"

            raw.contains("timeout", ignoreCase = true) -> "连接超时，请检查地址、端口与防火墙"
            raw.contains("ERR DB index is out of range", ignoreCase = true) ->
                "服务端限制了库的数量，无法使用 $DATABASE 号库（请检查 redis.conf 的 databases）"

            raw.contains("SELECT", ignoreCase = true) -> "无法切换到 $DATABASE 号库"
            else -> "请检查主机、端口、密码与网络可达性"
        }
        return "连接 Redis 失败：$hint。${raw.ifBlank { "（驱动未返回原因）" }}"
    }

    private companion object {
        /** 与 `application.yaml` 的 `spring.data.redis.database` 保持一致。 */
        const val DATABASE = 5
        const val TIMEOUT_SECONDS = 5L
        val VERSION_PATTERN = Regex("redis_version:([^\\r\\n]+)")
    }
}
