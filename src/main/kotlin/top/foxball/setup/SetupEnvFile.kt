package top.foxball.setup

/**
 * SetupEnvFile 配置引导组件说明。
 *
 * 该文件负责系统初始化向导中的配置探测、持久化、校验或 Web 入口逻辑。
 */
/**
 * SetupEnvFile 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import top.foxball.cartask.config.DotenvLoader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.SecureRandom
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*


/**
 * SetupEnvTemplate 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
/**
 * SetupEnvTemplate 的职责与行为说明。
 * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
 */
object SetupEnvTemplate {
    
    private const val DEFAULT_REDIS_TIMEOUT = "2s"
    private const val DEFAULT_DDL_AUTO = "update"
    private const val DEFAULT_CORS_ORIGINS = "http://localhost:8090,http://127.0.0.1:8090"
    private const val DEFAULT_KEYTOP_BASE_URL = "https://kp-open.keytop.cn/unite-api"
    private const val SESSION_TTL = "2h"
    private const val CLOCK_SKEW = "30s"
    
    
    /**
     * render 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    /**
     * render 的职责与行为说明。
     * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
     */
    fun render(
        values: Map<String, String>,
        existing: Map<String, String>,
        frontendOrigin: String?,
        generatedAt: LocalDateTime,
    ): String {
        val merged = values.toMutableMap()
        
        val signingKey = existing["JWT_SIGNING_KEY_LOCAL"]?.takeIf { it.isNotBlank() } ?: randomBase64Key()
        val storageKey = existing["JWT_STORAGE_ENCRYPTION_KEY"]?.takeIf { it.isNotBlank() } ?: randomBase64Key()
        
        val corsOrigins = corsOrigins(existing, frontendOrigin)
        
        return buildString {
            appendLine("# ============================================================")
            appendLine("#  由「配置引导」于 ${generatedAt} 生成")
            appendLine("#  手工修改后重启服务即可生效。")
            appendLine("#  想重新走一遍引导：把下面这行取消注释后重启，再打开前端 /setup 页面。")
            appendLine("#  SETUP_MODE=true")
            appendLine("# ============================================================")
            appendLine()
            appendLine("# PostgreSQL 数据库")
            appendLine("DB_URL=${merged.quoted("DB_URL")}")
            appendLine("DB_USERNAME=${merged.quoted("DB_USERNAME")}")
            appendLine("DB_PASSWORD=${merged.quoted("DB_PASSWORD")}")
            appendLine("# 建表策略：update 保留数据并补齐结构；create 会清库重建。")
            appendLine("JPA_DDL_AUTO=${merged.quoted("JPA_DDL_AUTO", DEFAULT_DDL_AUTO)}")
            appendLine()
            appendLine("# Redis：JWT 会话的在线状态与登录限流都放在这里，库号固定 5")
            appendLine("REDIS_HOST=${merged.quoted("REDIS_HOST")}")
            appendLine("REDIS_PORT=${merged.quoted("REDIS_PORT")}")
            appendLine("REDIS_PASSWORD=${merged.quoted("REDIS_PASSWORD")}")
            appendLine("REDIS_TIMEOUT=${merged.quoted("REDIS_TIMEOUT", DEFAULT_REDIS_TIMEOUT)}")
            appendLine()
            appendLine("# 浏览器跨域白名单：填前端应用的访问地址，多个用逗号分隔")
            appendLine("CORS_ALLOWED_ORIGINS=$corsOrigins")
            appendLine("CORS_ALLOWED_ORIGIN_PATTERNS=")
            appendLine("CORS_ALLOW_CREDENTIALS=true")
            appendLine()
            appendLine("# 登录限流")
            appendLine("LOGIN_RATE_LIMIT_ENABLED=true")
            appendLine("LOGIN_RATE_LIMIT_MAX_ATTEMPTS=5")
            appendLine("LOGIN_RATE_LIMIT_WINDOW=15m")
            appendLine()
            appendLine("# JWT：签名密钥与 token 密文密钥由此处随机生成，更换会让所有已登录会话失效")
            appendLine("JWT_ISSUER=carTask")
            appendLine("JWT_AUDIENCE=carTask-api")
            appendLine("JWT_ACTIVE_SIGNING_KEY_ID=local")
            appendLine("JWT_SIGNING_KEY_LOCAL=$signingKey")
            appendLine("JWT_TTL=$SESSION_TTL")
            appendLine("JWT_CLOCK_SKEW=$CLOCK_SKEW")
            appendLine("JWT_STORAGE_ENCRYPTION_KEY=$storageKey")
            appendLine("JWT_STORAGE_ENCRYPTION_KEY_ID=local")
            appendLine()
            appendLine("# 文件存储：存储根目录留空表示用服务的工作目录；下载地址必须是外部可访问的绝对 HTTP(S) 地址")
            appendLine("FILE_STORAGE_ROOT=${merged.quoted("FILE_STORAGE_ROOT")}")
            appendLine("FILE_BASE_URL=${merged.quoted("FILE_BASE_URL")}")
            appendLine()
            appendLine("# 科拓开放平台")
            appendLine("KEYTOP_BASE_URL=${merged.quoted("KEYTOP_BASE_URL", DEFAULT_KEYTOP_BASE_URL)}")
            appendLine("KEYTOP_APP_ID=${merged.quoted("KEYTOP_APP_ID")}")
            appendLine("KEYTOP_PARK_ID=${merged.quoted("KEYTOP_PARK_ID")}")
            appendLine("KEYTOP_PARK_NAME=${merged.quoted("KEYTOP_PARK_NAME")}")
            appendLine("KEYTOP_APP_SECRET=${merged.quoted("KEYTOP_APP_SECRET")}")
            appendLine()
            appendLine("# 短信（阿里云）：不启用时保持 SMS_ENABLED=false")
            appendLine("SMS_ENABLED=${merged.quoted("SMS_ENABLED", "false")}")
            appendLine("SMS_ACCESS_KEY_ID=${merged.quoted("SMS_ACCESS_KEY_ID")}")
            appendLine("SMS_ACCESS_KEY_SECRET=${merged.quoted("SMS_ACCESS_KEY_SECRET")}")
            appendLine("SMS_SIGN_NAME=${merged.quoted("SMS_SIGN_NAME")}")
            appendLine("SMS_TEMPLATE_CODE=${merged.quoted("SMS_TEMPLATE_CODE")}")
            appendLine()
            appendLine("# 超级管理员：首次启动时按这里的值创建账号。")
            appendLine("# 账号建好后服务会清掉下面的密码并关掉本段，无需手工处理。")
            appendLine("ADMIN_INITIALIZER_ENABLED=true")
            appendLine("ADMIN_INITIALIZER_USERNAME=${merged.quoted("ADMIN_INITIALIZER_USERNAME")}")
            appendLine("ADMIN_INITIALIZER_PASSWORD=${merged.quoted("ADMIN_INITIALIZER_PASSWORD")}")
            appendLine("ADMIN_INITIALIZER_FORCE_WRITE=false")
        }
    }
    
    
    /**
     * corsOrigins 的职责与行为说明。
     * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
     */
    private fun corsOrigins(existing: Map<String, String>, frontendOrigin: String?): String {
        val configured = existing["CORS_ALLOWED_ORIGINS"].orEmpty()
            .split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
        val defaults = DEFAULT_CORS_ORIGINS.split(',').map(String::trim)
        val submitted = frontendOrigin?.trim().orEmpty().takeIf { it.isNotEmpty() }
        return ((configured.ifEmpty { defaults }) + listOfNotNull(submitted))
            .distinct()
            .joinToString(",")
    }
    
    
    /**
     * randomBase64Key 的职责与行为说明。
     * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
     */
    private fun randomBase64Key(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }
    
    
    /**
     * Map 的职责与行为说明。
     * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
     */
    private fun Map<String, String>.quoted(key: String, default: String = ""): String {
        val value = this[key]?.takeIf { it.isNotBlank() } ?: default
        require(!value.contains('\n') && !value.contains('\r')) { "$key 不能包含换行" }
        return if (value.contains('#') || value != value.trim()) "\"$value\"" else value
    }
}


@Component
/**
 * SetupEnvFile 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
/**
 * SetupEnvFile 的职责与行为说明。
 * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
 */
class SetupEnvFile(
    private val properties: SetupProperties,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    
    
    /**
     * write 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    /**
     * write 的职责与行为说明。
     * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
     */
    fun write(values: Map<String, String>, frontendOrigin: String?, now: LocalDateTime): Path? {
        val path = properties.envPath()
        val existing = DotenvLoader.read(path)
        val backup = backup(path, now)
        val content = SetupEnvTemplate.render(values, existing, frontendOrigin, now)
        
        path.parent?.let { Files.createDirectories(it) }
        val temporary = path.resolveSibling("${path.fileName}.tmp")
        Files.writeString(temporary, content)
        Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        runCatching {
            Files.setPosixFilePermissions(
                path,
                java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"),
            )
        }
        logger.info(
            "配置已写入 {}（{} 字节，备份: {}）",
            path,
            content.toByteArray(StandardCharsets.UTF_8).size,
            backup ?: "无"
        )
        return backup
    }
    
    
    /**
     * backup 的职责与行为说明。
     * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
     */
    private fun backup(path: Path, now: LocalDateTime): Path? {
        if (!Files.exists(path)) return null
        val stamp = now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
        val backup = path.resolveSibling("${path.fileName}.bak.$stamp")
        Files.copy(path, backup, StandardCopyOption.REPLACE_EXISTING)
        return backup
    }
}





