package top.foxball.setup

/**
 * 引导步骤与环境变量的对应关系。
 *
 * 一份声明同时兜住三件事：向导每一步能写哪些键（[keys]，写别的键一律拒绝）、这一步填到什么程度
 * 才算完成（[required]）、以及重新进入引导模式时从既有 `.env` 里捞回哪些值（仍按 [keys] 取）。
 * 三处各写一份的话，加一个键就会漏掉其中一处，而漏掉的表现是「某项配置在向导里填了却没生效」。
 */
enum class SetupSection(
    val id: String,
    val title: String,
    val keys: List<String>,
    val required: List<String>,
    /** 值属于机密、不原样回给前端，只回「已保存」标记。 */
    val secrets: List<String>,
) {
    DATABASE(
        id = "database",
        title = "数据库",
        keys = listOf("DB_URL", "DB_USERNAME", "DB_PASSWORD"),
        // 密码允许为空：本机信任认证、或者已经配好 pgpass 的部署确实没有密码。
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
        // 短信是可选项，允许整步跳过；启用时由服务端另外校验凭据是否齐全。
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

    /** 该步骤是否已具备完成条件。 */
    fun satisfiedBy(values: Map<String, String>): Boolean =
        required.all { !values[it].isNullOrBlank() }

    /** 只取属于本步骤的键，用来把外部传入的键集裁剪回允许范围。 */
    fun slice(values: Map<String, String>): Map<String, String> =
        keys.filter { values.containsKey(it) }.associateWith { values.getValue(it) }

    companion object {
        fun byId(id: String): SetupSection? = entries.firstOrNull { it.id == id }

        /** 所有步骤写过的键的并集，供渲染 `.env` 时做白名单校验。 */
        val allKeys: Set<String> = entries.flatMap { it.keys }.toSet()

        val allSecrets: Set<String> = entries.flatMap { it.secrets }.toSet()
    }
}
