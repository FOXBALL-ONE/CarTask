package top.foxball.setup

/**
 * SetupMode 配置引导组件说明。
 *
 * 该文件负责系统初始化向导中的配置探测、持久化、校验或 Web 入口逻辑。
 */
/**
 * SetupMode 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */

import top.foxball.cartask.config.DotenvLoader
import java.nio.file.Path


/**
 * SetupMode 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
/**
 * SetupMode 的职责与行为说明。
 * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
 */
object SetupMode {
    
    const val FORCE_KEY = "SETUP_MODE"
    
    
    const val DATABASE_URL_KEY = "DB_URL"
    
    
    /**
     * required 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    /**
     * required 的职责与行为说明。
     * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
     */
    fun required(): Boolean = required(effectiveEnvironment())
    
    
    /**
     * required 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    /**
     * required 的职责与行为说明。
     * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
     */
    fun required(environment: Map<String, String>): Boolean {
        when (environment[FORCE_KEY]?.trim()?.lowercase()) {
            "true", "1", "yes", "on" -> return true
            "false", "0", "no", "off" -> return false
        }
        return environment[DATABASE_URL_KEY].isNullOrBlank()
    }
    
    
    /**
     * effectiveEnvironment 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    /**
     * effectiveEnvironment 的职责与行为说明。
     * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
     */
    fun effectiveEnvironment(envPath: Path = DotenvLoader.defaultPath()): Map<String, String> = buildMap {
        putAll(DotenvLoader.read(envPath))
        putAll(System.getenv())
        System.getProperty(FORCE_KEY)?.let { put(FORCE_KEY, it) }
        System.getProperty(DATABASE_URL_KEY)?.let { put(DATABASE_URL_KEY, it) }
    }
}





