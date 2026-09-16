package top.foxball.setup

/**
 * SetupProperties 配置引导组件说明。
 *
 * 该文件负责系统初始化向导中的配置探测、持久化、校验或 Web 入口逻辑。
 */
/**
 * SetupProperties 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */

import org.springframework.boot.context.properties.ConfigurationProperties
import top.foxball.cartask.config.DotenvLoader
import java.nio.file.Path


@ConfigurationProperties(prefix = "app.setup")
/**
 * SetupProperties 的职责与行为说明。
 * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
 */
data class SetupProperties(
    val token: String = "",
    val draftFile: String = ".setup-draft.json",
) {
    
    /**
     * envPath 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    /**
     * envPath 的职责与行为说明。
     * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
     */
    fun envPath(): Path = DotenvLoader.defaultPath()
    
    
    /**
     * draftPath 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    /**
     * draftPath 的职责与行为说明。
     * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
     */
    fun draftPath(): Path = Path.of(draftFile).toAbsolutePath().normalize()
}





