package top.foxball.cartask.logging

/** Last-resort redaction for legacy parameterized messages that predate structured logging. */
object LogSanitizer {
    private val secretKey = Regex(
        "(?i)(password|passwd|token|authorization|cookie|set-cookie|secret|signature|app-secret|face_info|faceInfo)(\\s*[=:]\\s*)([^,;\\s}]+)",
    )
    private val phoneKey = Regex("(?i)(phone|mobile|手机号)(\\s*[=:：]\\s*)(\\d{3})\\d+(\\d{4})")
    private val plateKey = Regex("(?i)(plateNo|plate_no|carNumber|car_number|车牌)(\\s*[=:：]\\s*)([^,;\\s}]+)")
    private val bearer = Regex("(?i)Bearer\\s+[A-Za-z0-9._~-]+")

    /**
     * sanitize：执行当前模块中的业务操作。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param value 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
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
