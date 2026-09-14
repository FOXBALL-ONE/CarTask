package top.foxball.cartask

import org.slf4j.LoggerFactory
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.scheduling.annotation.EnableScheduling
import top.foxball.setup.SetupApplication
import top.foxball.setup.SetupMode
import top.foxball.setup.SetupRestartSignal

@SpringBootApplication
@EnableScheduling
class CarTaskApplication

/**
 * 入口。按 [SetupMode] 的判定在「配置引导」与「正常运行」两套应用之间二选一。
 *
 * 判定必须在 Spring 起来之前做：正常应用的上下文在数据源连不上时根本刷不起来，而配置模式要做的
 * 恰恰就是收集这份数据源配置。用一个 JVM 内的循环换取「配置完成即自动重启」，是因为现场实施人员
 * 手上往往只有一个 `java -jar` 的终端窗口，让他们在配置完成后再敲一次命令，等于把一个可自动完成的
 * 步骤变成一次可能出错的沟通。
 *
 * 正常的启动路径只经过一次循环就返回；控制台里 Tomcat 的非守护线程继续撑着进程。
 */
fun main(args: Array<String>) {
    val logger = LoggerFactory.getLogger("top.foxball.cartask.Bootstrap")
    var setup = SetupMode.required()

    while (true) {
        val application = SpringApplication(if (setup) SetupApplication::class.java else CarTaskApplication::class.java)
        val context = application.run(*args)
        if (!setup) return

        val signal = context.getBean(SetupRestartSignal::class.java)
        val restart = signal.await()
        context.close()
        if (!restart) return

        setup = SetupMode.required()
        logger.info("配置引导已结束，按新写入的配置重新启动（引导模式={}）", setup)
    }
}
