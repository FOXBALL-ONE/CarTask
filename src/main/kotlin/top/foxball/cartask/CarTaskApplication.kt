package top.foxball.cartask

import org.slf4j.LoggerFactory
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.scheduling.annotation.EnableScheduling
import top.foxball.cartask.config.AppRestartSignal
import top.foxball.setup.SetupApplication
import top.foxball.setup.SetupMode
import top.foxball.setup.SetupRestartSignal

@SpringBootApplication
@EnableScheduling
class CarTaskApplication

/**
 * 启动入口，兼作「重启宿主」。
 *
 * .env 里的配置只在启动时绑定成 Bean，运行中改文件不会生效，所以配置页保存后
 * 需要重启整个上下文。setup 模式（写 .env）与系统配置页的重载走同一条路径：
 * 各自发一个重启信号，这里收到后关掉旧上下文、按新的 .env 重新 run 一遍。
 *
 * 两个信号分属不同包，只因 @SpringBootApplication 默认只扫描自己所在的包：
 * SetupRestartSignal 在 top.foxball.setup（只被 SetupApplication 扫到），
 * AppRestartSignal 在 top.foxball.cartask.config（只被 CarTaskApplication 扫到）。
 */
fun main(args: Array<String>) {
    val logger = LoggerFactory.getLogger("top.foxball.cartask.Bootstrap")
    var setup = SetupMode.required()

    while (true) {
        val application = SpringApplication(if (setup) SetupApplication::class.java else CarTaskApplication::class.java)
        val context = application.run(*args)

        val restart = if (setup) {
            val requested = context.getBean(SetupRestartSignal::class.java).await()
            if (requested) logger.info("配置引导已结束，按新写入的配置重新启动（引导模式={}）", SetupMode.required())
            requested
        } else {
            val requested = context.getBean(AppRestartSignal::class.java).await()
            if (requested) logger.info("收到配置重载请求，按新写入的配置重新启动")
            requested
        }

        context.close()
        if (!restart) return

        setup = SetupMode.required()
    }
}
