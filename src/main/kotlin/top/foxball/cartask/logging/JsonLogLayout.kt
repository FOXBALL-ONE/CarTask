package top.foxball.cartask.logging

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxyUtil
import ch.qos.logback.core.LayoutBase
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Emits one escaped JSON object per line without serializing arbitrary application objects. */
class JsonLogLayout : LayoutBase<ILoggingEvent>() {
    var service: String = "carTask"
    var instance: String = System.getenv("HOSTNAME")?.ifBlank { null } ?: "local"
    var timeZone: String = "Asia/Shanghai"
    var includeException: Boolean = true
    var maxMessageLength: Int = 8192
    var maxExceptionLength: Int = 16384

    private val formatter: DateTimeFormatter
        get() = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.of(timeZone))

    /**
     * doLayout：执行当前模块中的业务操作。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param event 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
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

    /**
     * truncate：执行当前模块中的业务操作。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param limit 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    private fun String.truncate(limit: Int): String {
        if (length <= limit) return this
        return take(limit.coerceAtLeast(1)) + "...[truncated]"
    }

    /**
     * appendJsonValue：执行当前模块中的业务操作。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param value 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    private fun StringBuilder.appendJsonValue(value: Any?) {
        when (value) {
            null -> append("null")
            is Number, is Boolean -> append(value)
            else -> append('"').append(escape(value.toString())).append('"')
        }
    }

    /**
     * escape：执行当前模块中的业务操作。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param value 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
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
