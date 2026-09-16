package top.foxball.cartask.shared

/**
 * SerialNumbers 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */


/**
 * SerialNumbers 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
object SerialNumbers {
    private const val WIDTH = 4
    
    
    /**
     * format 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun format(id: Long): String = id.toString().padStart(WIDTH, '0')
    
    
    /**
     * matches 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun matches(id: Long?, keyword: String): Boolean =
        id != null && (id.toString().contains(keyword) || format(id).contains(keyword))
}


