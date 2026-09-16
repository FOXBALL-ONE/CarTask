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
