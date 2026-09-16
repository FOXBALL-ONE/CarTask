package top.foxball.cartask.logging

/**
 * JsonLogLayout 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxyUtil
import ch.qos.logback.core.LayoutBase
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter


/**
 * JsonLogLayout 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
class JsonLogLayout : LayoutBase<ILoggingEvent>() {
    var service: String = "carTask"
    var instance: String = System.getenv("HOSTNAME")?.ifBlank { null } ?: "local"
    var timeZone: String = "Asia/Shanghai"
    var includeException: Boolean = true
    var maxMessageLength: Int = 8192
    var maxExceptionLength: Int = 16384
    
    private val formatter: DateTimeFormatter
        get() = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.of(timeZone))
    
    
    override fun doLayout(event: ILoggingEvent): String {
        val fields = linkedMapOf<String, Any?>()
        fields["timestamp"] = formatter.format(Instant.ofEpochMilli(event.timeStamp))
        fields["level"] = event.level.levelStr
        fields["service"] = service
        fields["instance"] = instance
        fields["logger"] = event.loggerName
        fields["message"] = if (event.markerList?.any { it.name == "KEYTOP_RAW_PAYLOAD" } == true) {
            event.formattedMessage
        } else {
            LogSanitizer.sanitize(event.formattedMessage).truncate(maxMessageLength)
        }
        val mdc = event.mdcPropertyMap
        listOf(
            "request_id",
            "trace_id",
            "actor_type",
            "actor_id",
            "actor_role",
            "source_ip",
            "source_system",
            "operation",
            "target_type",
            "target_id",
            "duration_ms",
            "error_code",
            "http_method",
            "http_path",
            "http_status"
        ).forEach { key ->
            mdc[key]?.takeIf(String::isNotBlank)?.let {
                fields[key] = if (key == "duration_ms" || key == "http_status") it.toLongOrNull() ?: it else it
            }
        }
        if (includeException) {
            event.throwableProxy?.let {
                fields["exception"] =
                    LogSanitizer.sanitize(ThrowableProxyUtil.asString(it)).truncate(maxExceptionLength)
            }
        }
        return buildString {
            append('{')
            fields.entries.forEachIndexed { index, (key, value) ->
                if (index > 0) append(',')
                append('"').append(escape(key)).append("\":")
                appendJsonValue(value)
            }
            append("}\n")
        }
    }
    
    
    private fun String.truncate(limit: Int): String {
        if (length <= limit) return this
        return take(limit.coerceAtLeast(1)) + "...[truncated]"
    }
    
    
    private fun StringBuilder.appendJsonValue(value: Any?) {
        when (value) {
            null -> append("null")
            is Number, is Boolean -> append(value)
            else -> append('"').append(escape(value.toString())).append('"')
        }
    }
    
    
    private fun escape(value: String): String = buildString(value.length + 8) {
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) append("\\u%04x".format(character.code)) else append(character)
            }
        }
    }
}


