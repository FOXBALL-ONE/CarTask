package top.foxball.cartask.audit

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import top.foxball.cartask.authentication.CurrentUserPrincipal
import top.foxball.cartask.entity.AuditEvent
import top.foxball.cartask.repository.AuditEventRepository
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor
import java.util.*

@Service
class AuditServiceImpl(
    private val repository: AuditEventRepository,
    private val objectMapper: ObjectMapper,
    private val meterRegistry: MeterRegistry? = null,
) : AuditService {
    @Transactional
    /**
     * record：执行当前模块中的业务操作。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param command 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    override fun record(command: AuditCommand): AuditEvent {
        val request = AuditRequestContext.current()
        val sourceSystem =
            (command.sourceSystem?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: request?.sourceSystem
            ?: "SYSTEM").take(32)
        val idempotencyKey = command.idempotencyKey?.trim()?.takeIf { it.isNotEmpty() }?.take(256)
        val targetType = command.targetType
            .replace(Regex("[\\u0000-\\u001F\\u007F]"), "")
            .trim()
            .take(64)
        require(targetType.isNotEmpty()) { "审计目标类型不能为空" }
        val existing = idempotencyKey?.let {
            repository.findBySourceSystemAndActionAndIdempotencyKey(sourceSystem, command.action.code, it)
        }
        if (existing != null) {
            meterRegistry?.counter("audit_events_reused_total", "action", command.action.code)?.increment()
            return existing
        }

        val authentication = SecurityContextHolder.getContext().authentication
        val principal = authentication?.principal as? CurrentUserPrincipal
        val actorType = when {
            principal != null -> AuditEvent.ActorType.USER
            sourceSystem == "SYSTEM" || sourceSystem == "SCHEDULER" -> AuditEvent.ActorType.SYSTEM
            sourceSystem != "WEB" -> AuditEvent.ActorType.EXTERNAL
            else -> AuditEvent.ActorType.ANONYMOUS
        }
        val actorUsername =
            principal?.username ?: if (actorType == AuditEvent.ActorType.SYSTEM) sourceSystem else "anonymous"
        val recordedAt = LocalDateTime.now()
        val partitionKey = recordedAt.format(PARTITION_FORMATTER)
        repository.lockPartition(partitionKey)
        // 幂等查询必须在分区锁内再次执行，避免并发请求同时通过首次不存在检查。
        val existingAfterLock = idempotencyKey?.let {
            repository.findBySourceSystemAndActionAndIdempotencyKey(sourceSystem, command.action.code, it)
        }
        if (existingAfterLock != null) {
            meterRegistry?.counter("audit_events_reused_total", "action", command.action.code)?.increment()
            return existingAfterLock
        }
        val previous = repository.findTopByPartitionKeyOrderBySequenceNoDesc(partitionKey)
        val sequence = (previous?.sequenceNo ?: 0L) + 1L
        val event = AuditEvent().apply {
            eventId = UUID.randomUUID()
            occurredAt = command.occurredAt
            this.recordedAt = recordedAt
            requestId = request?.requestId
            this.actorType = actorType
            actorUserId = principal?.userId
            this.actorUsername = actorUsername.take(128)
            actorRole = principal?.role
            authorities = principal?.permissions?.sorted()?.let { objectMapper.writeValueAsString(it) }
            action = command.action.code
            category = command.action.category
            riskLevel = command.action.riskLevel
            this.targetType = targetType
            targetId = command.targetId
                ?.replace(Regex("[\\u0000-\\u001F\\u007F]"), "")
                ?.trim()
                ?.take(128)
                ?.takeIf { it.isNotEmpty() }
            targetSummary = serialize(command.targetSummary)
            scopeSummary = serialize(command.scopeSummary)
            result = command.result
            reasonCode = command.reasonCode?.take(128)
            reason = sanitizeReason(command.reason)
            beforeData = serialize(command.beforeData)
            afterData = serialize(command.afterData)
            sourceIp = request?.sourceIp?.take(64)
            userAgent = request?.userAgent?.take(512)
            this.sourceSystem = sourceSystem.take(32)
            this.partitionKey = partitionKey
            sequenceNo = sequence
            previousHash = previous?.eventHash
            this.idempotencyKey = idempotencyKey
        }
        event.eventHash = AuditEventHash.calculate(event)
        return try {
            repository.save(event).also {
                meterRegistry?.counter(
                    "audit_events_written_total",
                    "action", event.action,
                    "result", event.result.name,
                )?.increment()
            }
        } catch (error: RuntimeException) {
            meterRegistry?.counter("audit_write_errors_total", "action", command.action.code)?.increment()
            throw error
        }
    }

    /**
     * serialize：转换、构建或格式化数据。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param value 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    private fun serialize(value: Map<String, Any?>?): String? = value
        ?.let(::sanitizeMap)
        ?.let(objectMapper::writeValueAsString)

    /**
     * sanitizeMap：执行当前模块中的业务操作。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param value 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    private fun sanitizeMap(value: Map<String, Any?>): Map<String, Any?> = value
        .asSequence()
        .filter { (key, _) -> key in SAFE_KEYS && SENSITIVE_KEYS.none(key.lowercase()::contains) }
        .associate { (key, item) -> key.take(128) to sanitizeValue(item) }
        .toSortedMap()

    /**
     * sanitizeValue：执行当前模块中的业务操作。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param value 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    private fun sanitizeValue(value: Any?): Any? = when (value) {
        is Map<*, *> -> sanitizeMap(value.entries.associate { it.key.toString() to it.value })
        is Iterable<*> -> value.map(::sanitizeValue)
        is CharSequence -> value.toString().take(2048)
        is Number, is Boolean, is Enum<*>, is TemporalAccessor, is UUID -> value.toString()
        else -> null
    }

    /**
     * sanitizeReason：执行当前模块中的业务操作。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param value 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    private fun sanitizeReason(value: String?): String? = value
        ?.replace(Regex("[\\u0000-\\u001F\\u007F]"), " ")
        ?.replace(Regex("(?i)(password|credential|token|secret|authorization)\\s*[=:]\\s*[^,;\\s]+"), "$1=[REDACTED]")
        ?.trim()
        ?.take(1024)

    private companion object {
        val PARTITION_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM")
        val SENSITIVE_KEYS = setOf(
            "password",
            "credential",
            "passwordhash",
            "jwt",
            "token",
            "secret",
            "key",
            "face_info",
            "faceinfo",
            "file_content",
            "content_bytes",
        )

        /**
         * 审计摘要只允许业务白名单字段，未知字段默认丢弃。
         *
         * 这是有意为之的收敛：beforeData/afterData 由调用方随手拼 map，不设白名单就迟早会把
         * 身份证、人脸 URL 这类东西写进审计表。代价是**新增审计字段必须同步加到本集合**，
         * 否则它会被静默丢掉——只看到 `{}` 的开发者很难想到问题出在这里。
         */
        val SAFE_KEYS = setOf(
            "username", "role", "department_id", "department_code", "enabled", "status", "name", "permissions",
            "review_status", "synchronized", "car_number", "in_and_out", "in_and_out_time",
            "release_channel", "operator_name", "original_filename", "size_bytes", "content_type",
            "code", "sample_codes", "person_id", "deleted", "method", "path", "token_id_hash",
            "record_count", "occurred_from", "occurred_to", "action", "target_type",
            // 数据备份的产物摘要。少写一个键，它就会被静默丢掉，只留下一个空 {}
            // ——新增审计字段时必须同步加到这里。
            "include_files", "table_count", "row_count", "file_count", "missing_files",
            "sql_bytes", "archive_bytes", "duration_ms",
            // 同步周期改动的摘要。字段名不能用 task_key：SAFE_KEYS 之外还有一道
            // SENSITIVE_KEYS 子串过滤，"key" 会被当成敏感字段整条丢掉。
            "task", "cron", "previous_cron",
            // 车辆进出申请登记的摘要。同理不能用 apply_key / monthly_card_key。
            "plate", "owner", "valid_from", "valid_to", "monthly_card", "card_id", "sync_message",
            // 顺带建档的痕迹：不记下建了哪个账号，事后无从知道这条申请开通了谁的登录权限。
            "created_account", "created_owner", "created_plate",
        )
    }
}
