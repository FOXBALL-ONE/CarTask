package top.foxball.cartask.logging

import org.springframework.boot.context.properties.ConfigurationProperties
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Locale

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

    private fun requireSize(value: String, name: String) {
        require(runCatching { sizeBytes(value) }.isSuccess) { "$name must be a positive size such as 256MB or 10GB." }
    }

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
