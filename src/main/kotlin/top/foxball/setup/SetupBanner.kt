package top.foxball.setup

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.web.server.context.WebServerApplicationContext
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * 启动横幅：把「现在是配置模式、口令是什么、配置会写到哪」一次性说清楚。
 *
 * 引导页需要一个口令才能用，而口令只印在这里，所以横幅必须足够醒目且自带上下文——实施人员拿到的
 * 通常只有一行启动日志的截图或一段粘贴的终端输出，少了其中任何一项都要再来回问一轮。
 *
 * 同时列出本机的内网地址：配置文件里的 `FILE_BASE_URL`、以及从别的机器访问引导页，都要用实际地址
 * 而不是 `localhost`，而部署现场未必有人知道这台机器的 IP。
 */
@Component
class SetupBanner(
    private val token: SetupToken,
    private val properties: SetupProperties,
    private val applicationContext: ApplicationContext,
) : ApplicationRunner {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * run：执行当前模块中的业务操作。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param args 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    override fun run(args: ApplicationArguments) {
        val port = (applicationContext as? WebServerApplicationContext)?.webServer?.port ?: DEFAULT_PORT
        logger.info(
            """

            ================================================================
              系统尚未初始化，当前运行在「配置引导模式」
              数据库与缓存都还没有连接，只有引导接口可用。

              配置口令   {}   （{}）
              配置文件   {}   （引导完成后写入）
              草稿文件   {}   （已完成步骤的暂存，可随时关闭浏览器）

              引导页     {}   ← 前端应用，默认跑在 8090
              接口地址   http://localhost:{}/api/setup

              完成配置后服务会自动重启进入正常运行模式。
            ================================================================
            """.trimIndent(),
            token.value(),
            if (token.fromConfiguration) "来自 SETUP_TOKEN" else "本次随机生成，重启会重新生成",
            properties.envPath(),
            properties.draftPath(),
            frontendHint(port),
            port,
        )
    }

    /**
     * 引导页在前端应用里，后端无从得知它部署在哪，只能给出「本机 + 默认端口」这一条最可能的路径，
     * 再补上内网地址供远程访问时套用。
     */
    private fun frontendHint(port: Int): String {
        val addresses = localAddresses()
        val suffix = if (addresses.isEmpty()) "" else "（内网可用：${addresses.joinToString("、")}，把主机名换成对应地址）"
        return "http://localhost:$FRONTEND_PORT/setup $suffix"
    }

    /**
     * localAddresses：执行当前模块中的业务操作。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    private fun localAddresses(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.asSequence() }
            .filterIsInstance<Inet4Address>()
            .map { it.hostAddress }
            .toList()
    }.getOrDefault(emptyList())

    private companion object {
        const val DEFAULT_PORT = 8080
        const val FRONTEND_PORT = 8090
    }
}
