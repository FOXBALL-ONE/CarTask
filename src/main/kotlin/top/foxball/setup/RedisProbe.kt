package top.foxball.setup

/**
 * RedisProbe 配置引导组件说明。
 *
 * 该文件负责系统初始化向导中的配置探测、持久化、校验或 Web 入口逻辑。
 */
/**
 * RedisProbe 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */

import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * RedisProbeResult 的职责与行为说明。
 * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
 */
data class RedisProbeResult(
    val version: String,
    val database: Int,
)


@Component
/**
 * RedisProbe 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
/**
 * RedisProbe 的职责与行为说明。
 * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
 */
class RedisProbe {
    
    
    /**
     * probe 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    /**
     * probe 的职责与行为说明。
     * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
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
     * redisVersion 的职责与行为说明。
     * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
     */
    private fun redisVersion(info: String?): String =
        VERSION_PATTERN.find(info.orEmpty())?.groupValues?.get(1) ?: "未知"
    
    
    /**
     * describe 的职责与行为说明。
     * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
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
        
        const val DATABASE = 5
        const val TIMEOUT_SECONDS = 5L
        val VERSION_PATTERN = Regex("redis_version:([^\\r\\n]+)")
    }
}





