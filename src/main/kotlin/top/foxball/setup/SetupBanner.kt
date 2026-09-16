package top.foxball.setup

/**
 * SetupBanner 配置引导组件说明。
 *
 * 该文件负责系统初始化向导中的配置探测、持久化、校验或 Web 入口逻辑。
 */
/**
 * SetupBanner 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.web.server.context.WebServerApplicationContext
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component
import java.net.Inet4Address
import java.net.NetworkInterface


@Component
/**
 * SetupBanner 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
/**
 * SetupBanner 的职责与行为说明。
 * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
 */
class SetupBanner(
    private val token: SetupToken,
    private val properties: SetupProperties,
    private val applicationContext: ApplicationContext,
) : ApplicationRunner {
    private val logger = LoggerFactory.getLogger(javaClass)
    
    
    /**
     * run 的职责与行为说明。
     * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
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
     * frontendHint 的职责与行为说明。
     * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
     */
    private fun frontendHint(port: Int): String {
        val addresses = localAddresses()
        val suffix = if (addresses.isEmpty()) "" else "（内网可用：${addresses.joinToString("、")}，把主机名换成对应地址）"
        return "http://localhost:$FRONTEND_PORT/setup $suffix"
    }
    
    
    /**
     * localAddresses 的职责与行为说明。
     * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
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





