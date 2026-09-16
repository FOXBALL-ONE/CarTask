package top.foxball.cartask.logging

/**
 * LogSanitizer 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */


/**
 * LogSanitizer 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
object LogSanitizer {
    private val secretKey = Regex(
        "(?i)(password|passwd|token|authorization|cookie|set-cookie|secret|signature|app-secret|face_info|faceInfo)(\\s*[=:]\\s*)([^,;\\s}]+)",
    )
    private val phoneKey = Regex("(?i)(phone|mobile|手机号)(\\s*[=:：]\\s*)(\\d{3})\\d+(\\d{4})")
    private val plateKey = Regex("(?i)(plateNo|plate_no|carNumber|car_number|车牌)(\\s*[=:：]\\s*)([^,;\\s}]+)")
    private val bearer = Regex("(?i)Bearer\\s+[A-Za-z0-9._~-]+")
    
    
    /**
     * sanitize 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun sanitize(value: String): String = value
        .replace(bearer, "Bearer [REDACTED]")
        .replace(secretKey) { "${it.groupValues[1]}${it.groupValues[2]}[REDACTED]" }
        .replace(phoneKey) { "${it.groupValues[1]}${it.groupValues[2]}${it.groupValues[3]}****${it.groupValues[4]}" }
        .replace(plateKey) { match ->
            val raw = match.groupValues[3].trim('"', '\'')
            val suffix = raw.takeLast(4)
            "${match.groupValues[1]}${match.groupValues[2]}***$suffix"
        }
}


