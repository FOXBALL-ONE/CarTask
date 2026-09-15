package top.foxball.cartask.logging

import org.springframework.boot.context.properties.ConfigurationProperties
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

@ConfigurationProperties(prefix = "app.logging")
data class LoggingProperties(
    val directory: String = Paths.get(System.getProperty("user.dir"), "logs").toString(),
    val timeZone: String = "Asia/Shanghai",
    val retentionDays: Int = 30,
    val totalSizeCap: String = "10GB",
    val maxFileSize: String = "256MB",
    val maxMessageLength: Int = 8192,
    val queueSize: Int = 8192,
) {
    val rootPath: Path = directory.trim().ifBlank { Paths.get(System.getProperty("user.dir"), "logs").toString() }
        .let(Paths::get).toAbsolutePath().normalize()
        .also {
            Files.createDirectories(it)
            require(Files.isDirectory(it) && Files.isWritable(it)) { "APP_LOG_DIR must be a writable directory." }
        }

    init {
        require(retentionDays in 1..3650) { "APP_LOG_RETENTION_DAYS must be between 1 and 3650." }
        require(queueSize in 256..65536) { "APP_LOG_QUEUE_SIZE must be between 256 and 65536." }
        require(maxMessageLength in 256..65536) { "APP_LOG_MAX_MESSAGE_LENGTH must be between 256 and 65536." }
        requireSize(maxFileSize, "APP_LOG_MAX_FILE_SIZE")
        requireSize(totalSizeCap, "APP_LOG_TOTAL_SIZE_CAP")
        require(sizeBytes(totalSizeCap) >= sizeBytes(maxFileSize)) {
            "APP_LOG_TOTAL_SIZE_CAP must be no smaller than APP_LOG_MAX_FILE_SIZE."
        }
        java.time.ZoneId.of(timeZone)
    }

    /**
     * requireSize：校验输入、状态或访问条件。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param value 参与本次处理的输入参数。
     * @param name 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    private fun requireSize(value: String, name: String) {
        require(runCatching { sizeBytes(value) }.isSuccess) { "$name must be a positive size such as 256MB or 10GB." }
    }

    /**
     * sizeBytes：执行当前模块中的业务操作。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param value 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    private fun sizeBytes(value: String): Long {
        val match = Regex("(?i)^(\\d+)(KB|MB|GB|TB)$").matchEntire(value.trim())
            ?: error("invalid size")
        val amount = match.groupValues[1].toLong()
        val multiplier = when (match.groupValues[2].uppercase(Locale.ROOT)) {
            "KB" -> 1024L
            "MB" -> 1024L * 1024
            "GB" -> 1024L * 1024 * 1024
            "TB" -> 1024L * 1024 * 1024 * 1024
            else -> error("invalid unit")
        }
        return Math.multiplyExact(amount, multiplier)
    }
}
