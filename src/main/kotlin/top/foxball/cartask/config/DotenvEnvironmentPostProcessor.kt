package top.foxball.cartask.config

/**
 * DotenvEnvironmentPostProcessor 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */

import org.springframework.boot.EnvironmentPostProcessor
import org.springframework.boot.SpringApplication
import org.springframework.core.env.ConfigurableEnvironment
import java.nio.file.Path


/**
 * DotenvEnvironmentPostProcessor 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
class DotenvEnvironmentPostProcessor @JvmOverloads constructor(
    private val dotenvPath: Path = DotenvLoader.defaultPath(),
) : EnvironmentPostProcessor {
    
    
    override fun postProcessEnvironment(environment: ConfigurableEnvironment, application: SpringApplication) {
        if ("test" in environment.activeProfiles) return
        DotenvLoader.addTo(environment, dotenvPath)
    }
}


