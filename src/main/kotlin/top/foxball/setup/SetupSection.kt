package top.foxball.setup

/**
 * SetupSection 配置引导组件说明。
 *
 * 该文件负责系统初始化向导中的配置探测、持久化、校验或 Web 入口逻辑。
 */
/**
 * SetupSection 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */


/**
 * SetupSection 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
enum class SetupSection(
    val id: String,
    val title: String,
    val keys: List<String>,
    val required: List<String>,
    
    val secrets: List<String>,
) {
    DATABASE(
        id = "database",
        title = "数据库",
        keys = listOf("DB_URL", "DB_USERNAME", "DB_PASSWORD"),
        required = listOf("DB_URL", "DB_USERNAME"),
        secrets = listOf("DB_PASSWORD"),
    ),
    REDIS(
        id = "redis",
        title = "Redis",
        keys = listOf("REDIS_HOST", "REDIS_PORT", "REDIS_PASSWORD"),
        required = listOf("REDIS_HOST", "REDIS_PORT"),
        secrets = listOf("REDIS_PASSWORD"),
    ),
    KEYTOP(
        id = "keytop",
        title = "科拓开放平台",
        keys = listOf("KEYTOP_BASE_URL", "KEYTOP_APP_ID", "KEYTOP_PARK_ID", "KEYTOP_PARK_NAME", "KEYTOP_APP_SECRET"),
        required = listOf("KEYTOP_BASE_URL", "KEYTOP_APP_ID", "KEYTOP_PARK_ID", "KEYTOP_APP_SECRET"),
        secrets = listOf("KEYTOP_APP_SECRET"),
    ),
    STORAGE(
        id = "storage",
        title = "文件存储",
        keys = listOf("FILE_STORAGE_ROOT", "FILE_BASE_URL"),
        required = listOf("FILE_STORAGE_ROOT", "FILE_BASE_URL"),
        secrets = emptyList(),
    ),
    SMS(
        id = "sms",
        title = "短信",
        keys = listOf(
            "SMS_ENABLED",
            "SMS_ACCESS_KEY_ID",
            "SMS_ACCESS_KEY_SECRET",
            "SMS_SIGN_NAME",
            "SMS_TEMPLATE_CODE",
        ),
        required = emptyList(),
        secrets = listOf("SMS_ACCESS_KEY_SECRET"),
    ),
    ADMINISTRATOR(
        id = "administrator",
        title = "管理员账号",
        keys = listOf(
            "ADMIN_INITIALIZER_ENABLED",
            "ADMIN_INITIALIZER_USERNAME",
            "ADMIN_INITIALIZER_PASSWORD",
            "ADMIN_INITIALIZER_FORCE_WRITE",
        ),
        required = listOf("ADMIN_INITIALIZER_USERNAME", "ADMIN_INITIALIZER_PASSWORD"),
        secrets = listOf("ADMIN_INITIALIZER_PASSWORD"),
    ),
    ;
    
    
    /**
     * satisfiedBy 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    /**
     * satisfiedBy 的职责与行为说明。
     * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
     */
    fun satisfiedBy(values: Map<String, String>): Boolean =
        required.all { !values[it].isNullOrBlank() }
    
    
    /**
     * slice 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    /**
     * slice 的职责与行为说明。
     * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
     */
    fun slice(values: Map<String, String>): Map<String, String> =
        keys.filter { values.containsKey(it) }.associateWith { values.getValue(it) }
    
    companion object {
        
        
        /**
         * byId 函数：执行与该组件职责相关的业务操作。
         * 参数和返回值遵循调用方与领域服务之间的约定。
         */
        /**
         * byId 的职责与行为说明。
         * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
         */
        fun byId(id: String): SetupSection? = entries.firstOrNull { it.id == id }
        
        
        val allKeys: Set<String> = entries.flatMap { it.keys }.toSet()
        
        val allSecrets: Set<String> = entries.flatMap { it.secrets }.toSet()
    }
}





