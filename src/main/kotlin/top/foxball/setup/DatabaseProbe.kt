package top.foxball.setup

/**
 * DatabaseProbe 配置引导组件说明。
 *
 * 该文件负责系统初始化向导中的配置探测、持久化、校验或 Web 入口逻辑。
 */
/**
 * DatabaseProbe 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */

import org.springframework.stereotype.Component
import java.sql.DriverManager
import java.util.*

/**
 * DatabaseProbeResult 的职责与行为说明。
 * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
 */
data class DatabaseProbeResult(
    val product: String,
    val version: String,
    val database: String,
    
    val tables: Int,
)


@Component
/**
 * DatabaseProbe 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
/**
 * DatabaseProbe 的职责与行为说明。
 * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
 */
class DatabaseProbe {
    
    
    /**
     * probe 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    /**
     * probe 的职责与行为说明。
     * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
     */
    fun probe(url: String, username: String, password: String): DatabaseProbeResult {
        val trimmedUrl = url.trim()
        if (!trimmedUrl.startsWith(JDBC_POSTGRES_PREFIX)) {
            throw SetupException("数据库连接串必须以 $JDBC_POSTGRES_PREFIX 开头（本系统只支持 PostgreSQL）")
        }
        if (username.isBlank()) {
            throw SetupException("数据库用户名不能为空")
        }
        
        val properties = Properties().apply {
            setProperty("user", username.trim())
            setProperty("password", password)
            setProperty("connectTimeout", CONNECT_TIMEOUT_SECONDS)
            setProperty("socketTimeout", SOCKET_TIMEOUT_SECONDS)
        }
        DriverManager.setLoginTimeout(CONNECT_TIMEOUT_SECONDS.toInt())
        
        val result = try {
            DriverManager.getConnection(trimmedUrl, properties).use { connection ->
                val metaData = connection.metaData
                DatabaseProbeResult(
                    product = metaData.databaseProductName ?: "未知",
                    version = metaData.databaseProductVersion ?: "未知",
                    database = connection.catalog ?: "未知",
                    tables = countTables(connection),
                )
            }
        } catch (exception: java.sql.SQLException) {
            throw SetupException(describe(exception), exception)
        }
        
        if (result.product.lowercase() != "postgresql") {
            throw SetupException("连接到的不是 PostgreSQL（报告为 ${result.product}），本系统只支持 PostgreSQL")
        }
        return result
    }
    
    
    /**
     * countTables 的职责与行为说明。
     * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
     */
    private fun countTables(connection: java.sql.Connection): Int = runCatching {
        connection.prepareStatement(TABLE_COUNT_SQL).use { statement ->
            statement.executeQuery().use { rows -> if (rows.next()) rows.getInt(1) else 0 }
        }
    }.getOrDefault(0)
    
    
    /**
     * describe 的职责与行为说明。
     * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
     */
    private fun describe(exception: java.sql.SQLException): String {
        val raw = generateSequence(exception as Throwable) { it.cause }
            .mapNotNull { it.message }
            .joinToString("；")
        val hint = when {
            raw.contains("Connection refused", ignoreCase = true) ||
                    raw.contains("连接被拒绝", ignoreCase = true) -> "目标主机或端口上没有 PostgreSQL 在监听"
            
            raw.contains("password authentication failed", ignoreCase = true) -> "用户名或密码不正确"
            raw.contains("does not exist", ignoreCase = true) -> "数据库不存在，请先在服务器上创建"
            raw.contains("timeout", ignoreCase = true) -> "连接超时，请检查地址、端口与防火墙"
            raw.contains("No suitable driver", ignoreCase = true) -> "连接串格式不正确"
            else -> "请检查连接串、用户名、密码与网络可达性"
        }
        return "连接数据库失败：$hint。${raw.ifBlank { "（驱动未返回原因）" }}"
    }
    
    private companion object {
        const val JDBC_POSTGRES_PREFIX = "jdbc:postgresql://"
        const val CONNECT_TIMEOUT_SECONDS = "5"
        const val SOCKET_TIMEOUT_SECONDS = "10"
        const val TABLE_COUNT_SQL =
            "select count(*) from information_schema.tables where table_schema = 'public' and table_type = 'BASE TABLE'"
    }
}





