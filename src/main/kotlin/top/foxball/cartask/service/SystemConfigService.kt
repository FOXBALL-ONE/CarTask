package top.foxball.cartask.service

import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment
import org.springframework.stereotype.Service
import top.foxball.cartask.config.DotenvLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 系统配置的读写。
 *
 * .env 才是配置的唯一真源：application.yaml 里的 ${SMS_ENABLED:false} 之类的占位符
 * 都从这个文件取值。所以这里「读」走 Environment 拿当前生效值（能带上 yaml 里的默认值），
 * 「写」直接改 .env 文件，保持注释与其它键原样不动，只替换目标行。
 *
 * 改完之后必须重载才能生效，原因见 AppRestartSignal 的说明。
 */
@Service
class SystemConfigService(
    private val environment: Environment,
) {
    private val logger = LoggerFactory.getLogger(javaClass)


    /** read：返回所有可编辑项的当前生效值；密钥一律以掩码返回，避免明文回显到浏览器。 */
    fun read(): Map<String, String> = EDITABLE_KEYS.associateWith { key ->
        val value = environment.getProperty(propertyKey(key)).orEmpty()
        if (key in SECRET_KEYS && value.isNotBlank()) MASK else value
    }


    /**
     * write：把提交上来的项写回 .env。
     *
     * 只接受白名单里的键，其余一律忽略而不是报错——前端与后端版本不一致时，
     * 多出来的字段不该让整次保存失败。密钥留空或原样回传掩码都表示「不修改」。
     */
    fun write(values: Map<String, String>) {
        val accepted = values.filterKeys { it in EDITABLE_KEYS }
        require(accepted.isNotEmpty()) { "没有可保存的配置项" }

        val effective = read().toMutableMap()
        val changed = linkedMapOf<String, String>()
        accepted.forEach { (key, raw) ->
            if (key in SECRET_KEYS && (raw.isBlank() || raw == MASK)) return@forEach
            val value = raw.trim()
            require(!value.contains('\n') && !value.contains('\r')) { "$key 不能包含换行" }
            effective[key] = value
            changed[key] = value
        }
        require(changed.isNotEmpty()) { "没有发生变化，密钥留空表示不修改" }

        validate(effective)
        writeEnv(changed)
    }


    /**
     * validate：跨字段校验，规则与启动时 CorsProperties.validate 保持一致，
     * 让用户在保存这一刻就拿到中文报错，而不是重启之后应用起不来。
     */
    private fun validate(values: Map<String, String>) {
        BOOLEAN_KEYS.forEach { key ->
            val raw = values[key].orEmpty()
            require(raw.isEmpty() || raw == "true" || raw == "false") { "$key 只能是 true 或 false" }
        }
        val allowCredentials = values["CORS_ALLOW_CREDENTIALS"].toBoolean()
        val origins = values["CORS_ALLOWED_ORIGINS"].orEmpty()
            .split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
        require(!(allowCredentials && "*" in origins)) {
            "允许携带凭据时不能把跨域来源配置为 *，请填写具体的前端地址"
        }

        values["KEYTOP_CAR_CAP_INFO_PHOTO_DOWNLOAD_INTERVAL"]?.let { raw ->
            if (raw.isNotBlank()) {
                val interval = parseDuration(raw)
                require(!interval.isNegative) { "图片下载间隔不能为负数" }
                require(interval <= java.time.Duration.ofSeconds(60)) { "图片下载间隔不能超过 60 秒" }
            }
        }

        values["KEYTOP_CAR_CAP_INFO_PHOTO_DOWNLOAD_CONCURRENCY"]?.let { raw ->
            if (raw.isNotBlank()) {
                val concurrency = raw.toIntOrNull()
                    ?: throw IllegalArgumentException("图片下载并行数必须是整数")
                require(concurrency in 1..16) { "图片下载并行数必须在 1 到 16 之间" }
            }
        }
    }


    private fun parseDuration(raw: String): java.time.Duration {
        return try {
            if (raw.matches(Regex("\\d+"))) {
                java.time.Duration.ofMillis(raw.toLong())
            } else {
                java.time.Duration.parse("PT$raw".uppercase())
            }
        } catch (exception: Exception) {
            throw IllegalArgumentException("无法解析时长格式：$raw（示例：200ms、1s、30s）")
        }
    }


    /** writeEnv：按行替换 .env 中的目标键，缺失的追加到末尾；写前留备份，写时走原子替换。 */
    private fun writeEnv(updates: Map<String, String>): Path {
        val path = DotenvLoader.defaultPath()
        val lines = if (Files.exists(path)) Files.readAllLines(path).toMutableList() else mutableListOf()

        updates.forEach { (key, value) ->
            val rendered = "$key=${quote(value)}"
            val index = lines.indexOfFirst { line ->
                line.trim().removePrefix("export ").trim().startsWith("$key=")
            }
            if (index >= 0) lines[index] = rendered else lines.add(rendered)
        }

        backup(path)
        path.parent?.let { Files.createDirectories(it) }
        val temporary = path.resolveSibling("${path.fileName}.tmp")
        Files.writeString(temporary, lines.joinToString("\n", postfix = "\n"))
        Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        logger.info("系统配置已写入 {}：{}", path, updates.keys.joinToString(", "))
        return path
    }


    /** backup：改动前留一份带时间戳的副本，配错了还能手工还原。 */
    private fun backup(path: Path) {
        if (!Files.exists(path)) return
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
        Files.copy(path, path.resolveSibling("${path.fileName}.bak.$stamp"), StandardCopyOption.REPLACE_EXISTING)
    }


    /** quote：含 # 或首尾空格的值必须加引号，否则 DotenvLoader 解析出来的值会被截断。 */
    private fun quote(value: String): String =
        if (value.contains('#') || value != value.trim()) "\"$value\"" else value


    /** propertyKey：环境变量名到 Spring 属性名的映射，用于从 Environment 读取生效值。 */
    private fun propertyKey(envKey: String): String = PROPERTY_KEYS.getValue(envKey)


    private companion object {
        const val MASK = "******"


        val EDITABLE_KEYS = setOf(
            "CORS_ALLOWED_ORIGINS",
            "CORS_ALLOW_CREDENTIALS",
            "SMS_ENABLED",
            "SMS_SKIP_VERIFICATION",
            "SMS_ACCESS_KEY_ID",
            "SMS_ACCESS_KEY_SECRET",
            "SMS_SIGN_NAME",
            "SMS_TEMPLATE_CODE",
            "FILE_BASE_URL",
            "KEYTOP_CAR_CAP_INFO_PHOTO_DOWNLOAD_INTERVAL",
            "KEYTOP_CAR_CAP_INFO_PHOTO_DOWNLOAD_CONCURRENCY",
        )


        val SECRET_KEYS = setOf("SMS_ACCESS_KEY_SECRET")


        val BOOLEAN_KEYS = setOf("CORS_ALLOW_CREDENTIALS", "SMS_ENABLED", "SMS_SKIP_VERIFICATION")


        val PROPERTY_KEYS = mapOf(
            "CORS_ALLOWED_ORIGINS" to "cartask.security.cors.allowed-origins",
            "CORS_ALLOW_CREDENTIALS" to "cartask.security.cors.allow-credentials",
            "SMS_ENABLED" to "cartask.sms.enabled",
            "SMS_SKIP_VERIFICATION" to "cartask.sms.skip-verification",
            "SMS_ACCESS_KEY_ID" to "cartask.sms.access-key-id",
            "SMS_ACCESS_KEY_SECRET" to "cartask.sms.access-key-secret",
            "SMS_SIGN_NAME" to "cartask.sms.sign-name",
            "SMS_TEMPLATE_CODE" to "cartask.sms.template-code",
            "FILE_BASE_URL" to "app.file.base-url",
            "KEYTOP_CAR_CAP_INFO_PHOTO_DOWNLOAD_INTERVAL" to "keytop.car-cap-info-photo-download-interval",
            "KEYTOP_CAR_CAP_INFO_PHOTO_DOWNLOAD_CONCURRENCY" to "keytop.car-cap-info-photo-download-concurrency",
        )
    }
}
