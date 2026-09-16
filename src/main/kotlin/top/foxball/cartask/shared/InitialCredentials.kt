package top.foxball.cartask.shared

/**
 * InitialCredentials 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */


/**
 * InitialCredentials 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
object InitialCredentials {
    
    const val PASSWORD = "Fqjg20221022"
    
    
    const val EMAIL_DOMAIN = "auto.local"
    
    
    /**
     * placeholderEmail 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun placeholderEmail(username: String): String = "$username@$EMAIL_DOMAIN"
}


