package top.foxball.setup

import org.springframework.stereotype.Component
import java.sql.DriverManager
import java.util.*

data class DatabaseProbeResult(
    val product: String,
    val version: String,
    val database: String,
    /** `public` 模式下已有的表数量；0 表示空库，将由本次启动自动建表。 */
    val tables: Int,
)

/**
 * 数据库连通性探测。
 *
 * 直接拿 JDBC 连，不建 `DataSource`：这套应用里数据源自动配置是被排除掉的（配置模式下还没有数据源
 * 可配），而连接成功与否本来就是这次探测唯一的结论。
 *
 * 探测里顺手数了一下 `public` 模式下已有的表：0 表示空库自动建表，非 0 表示这套库上已经有数据。
 * 后者在实施现场很常见——换了台机器重新部署，却连到了旧库上——页面上提前说一句，好过启动之后
 * 发现数据「不是自己的」再回头查。
 */
@Component
class DatabaseProbe {
    /**
     * probe：执行数据同步、探测或文件处理。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param url 参与本次处理的输入参数。
     * @param username 参与本次处理的输入参数。
     * @param password 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
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
            // URL 里显式写了这两个参数时以 URL 为准，这里只是给没写的连接串兜一个上限，
            // 否则填错主机会让页面一直转圈而不是报错。
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
     * countTables：查询或读取相关数据。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param connection 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    private fun countTables(connection: java.sql.Connection): Int = runCatching {
        connection.prepareStatement(TABLE_COUNT_SQL).use { statement ->
            statement.executeQuery().use { rows -> if (rows.next()) rows.getInt(1) else 0 }
        }
    }.getOrDefault(0)

    /**
     * 把驱动的原始报错翻成操作者能直接照做的动作。
     *
     * 驱动自己的消息是英文且分散在 `cause` 链里，实施人员看到的往往只有最后一句
     * "Connection refused"，而真正要知道的是「主机或端口不对」还是「密码不对」。
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
