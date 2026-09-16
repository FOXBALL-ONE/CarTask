package top.foxball.setup

/**
 * SetupController 配置引导组件说明。
 *
 * 该文件负责系统初始化向导中的配置探测、持久化、校验或 Web 入口逻辑。
 */
/**
 * SetupController 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import top.foxball.cartask.shared.Response
import top.foxball.cartask.shared.ResponseBuilder


@RestController
@RequestMapping("/api/setup")
/**
 * SetupController 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
/**
 * SetupController 的职责与行为说明。
 * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
 */
class SetupController(
    private val draftStore: SetupDraftStore,
    private val databaseProbe: DatabaseProbe,
    private val redisProbe: RedisProbe,
    private val keytopProbe: KeytopProbe,
    private val storageProbe: StorageProbe,
    private val smsProbe: SmsProbe,
    private val completion: SetupCompletion,
    private val properties: SetupProperties,
    private val responseBuilder: ResponseBuilder,
) {
    
    
    @GetMapping("/status")
            /**
             * status 函数：执行与该组件职责相关的业务操作。
             * 参数和返回值遵循调用方与领域服务之间的约定。
             */
            /**
             * status 的职责与行为说明。
             * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
             */
    fun status(): ResponseEntity<Response> {
        /**
         * Response 的职责与行为说明。
         * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
         */
        data class Response(
            @param:JsonProperty("setup_required") val setupRequired: Boolean,
            @param:JsonProperty("completed_steps") val completedSteps: List<String>,
            @param:JsonProperty("env_file") val envFile: String,
        )
        
        val draft = draftStore.read()
        val rs = Response(
            setupRequired = true,
            completedSteps = draft.steps,
            envFile = properties.envPath().toString(),
        )
        return responseBuilder.ok().data(rs).build()
    }
    
    
    @GetMapping("/draft")
            /**
             * draft 函数：执行与该组件职责相关的业务操作。
             * 参数和返回值遵循调用方与领域服务之间的约定。
             */
            /**
             * draft 的职责与行为说明。
             * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
             */
    fun draft(): ResponseEntity<Response> {
        /**
         * Response 的职责与行为说明。
         * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
         */
        data class Response(
            @param:JsonProperty("values") val values: Map<String, String>,
            @param:JsonProperty("configured_secrets") val configuredSecrets: List<String>,
            @param:JsonProperty("completed_steps") val completedSteps: List<String>,
        )
        
        val draft = draftStore.read()
        val rs = Response(
            values = draft.values.filterKeys { it !in SetupSection.allSecrets },
            configuredSecrets = draft.values
                .filterKeys { it in SetupSection.allSecrets }
                .filterValues { it.isNotBlank() }
                .keys
                .sorted(),
            completedSteps = draft.steps,
        )
        return responseBuilder.ok().data(rs).build()
    }
    
    
    @PostMapping("/database/verify")
            /**
             * verifyDatabase 函数：执行与该组件职责相关的业务操作。
             * 参数和返回值遵循调用方与领域服务之间的约定。
             */
            /**
             * verifyDatabase 的职责与行为说明。
             * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
             */
    fun verifyDatabase(
        @RequestParam(name = "url") url: String,
        @RequestParam(name = "username") username: String,
        @RequestParam(name = "password", defaultValue = "") password: String,
    ): ResponseEntity<Response> {
        /**
         * Response 的职责与行为说明。
         * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
         */
        data class Response(
            @param:JsonProperty("product") val product: String,
            @param:JsonProperty("version") val version: String,
            @param:JsonProperty("database") val database: String,
            @param:JsonProperty("tables") val tables: Int,
        )
        
        val result = databaseProbe.probe(url, username, password)
        draftStore.save(
            SetupSection.DATABASE,
            mapOf("DB_URL" to url.trim(), "DB_USERNAME" to username.trim(), "DB_PASSWORD" to password),
        )
        val rs = Response(result.product, result.version, result.database, result.tables)
        return responseBuilder.ok().data(rs).build()
    }
    
    
    @PostMapping("/redis/verify")
            /**
             * verifyRedis 函数：执行与该组件职责相关的业务操作。
             * 参数和返回值遵循调用方与领域服务之间的约定。
             */
            /**
             * verifyRedis 的职责与行为说明。
             * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
             */
    fun verifyRedis(
        @RequestParam(name = "host") host: String,
        @RequestParam(name = "port", defaultValue = "6379") port: Int,
        @RequestParam(name = "password", defaultValue = "") password: String,
    ): ResponseEntity<Response> {
        /**
         * Response 的职责与行为说明。
         * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
         */
        data class Response(
            @param:JsonProperty("version") val version: String,
            @param:JsonProperty("database") val database: Int,
        )
        
        val result = redisProbe.probe(host, port, password)
        draftStore.save(
            SetupSection.REDIS,
            mapOf(
                "REDIS_HOST" to host.trim(),
                "REDIS_PORT" to port.toString(),
                "REDIS_PASSWORD" to password,
            ),
        )
        val rs = Response(result.version, result.database)
        return responseBuilder.ok().data(rs).build()
    }
    
    
    @PostMapping("/keytop/verify")
            /**
             * verifyKeytop 函数：执行与该组件职责相关的业务操作。
             * 参数和返回值遵循调用方与领域服务之间的约定。
             */
            /**
             * verifyKeytop 的职责与行为说明。
             * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
             */
    fun verifyKeytop(
        @RequestParam(name = "base_url", defaultValue = "") baseUrl: String,
        @RequestParam(name = "app_id") appId: String,
        @RequestParam(name = "park_id") parkId: String,
        @RequestParam(name = "park_name", defaultValue = "") parkName: String,
        @RequestParam(name = "app_secret") appSecret: String,
    ): ResponseEntity<Response> {
        /**
         * Response 的职责与行为说明。
         * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
         */
        data class Response(
            @param:JsonProperty("message") val message: String,
            @param:JsonProperty("areas") val areas: Int?,
        )
        
        val effectiveBaseUrl = baseUrl.trim().ifEmpty { DEFAULT_KEYTOP_BASE_URL }
        val result = keytopProbe.probe(effectiveBaseUrl, appId, parkId, appSecret)
        draftStore.save(
            SetupSection.KEYTOP,
            mapOf(
                "KEYTOP_BASE_URL" to effectiveBaseUrl,
                "KEYTOP_APP_ID" to appId.trim(),
                "KEYTOP_PARK_ID" to parkId.trim(),
                "KEYTOP_PARK_NAME" to parkName.trim(),
                "KEYTOP_APP_SECRET" to appSecret.trim(),
            ),
        )
        val rs = Response(result.message, result.areas)
        return responseBuilder.ok().data(rs).build()
    }
    
    
    @PostMapping("/storage/verify")
            /**
             * verifyStorage 函数：执行与该组件职责相关的业务操作。
             * 参数和返回值遵循调用方与领域服务之间的约定。
             */
            /**
             * verifyStorage 的职责与行为说明。
             * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
             */
    fun verifyStorage(
        @RequestParam(name = "storage_root", defaultValue = "") storageRoot: String,
        @RequestParam(name = "base_url") baseUrl: String,
    ): ResponseEntity<Response> {
        /**
         * Response 的职责与行为说明。
         * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
         */
        data class Response(
            @param:JsonProperty("root") val root: String,
            @param:JsonProperty("base_url") val baseUrl: String,
        )
        
        val result = storageProbe.probe(storageRoot, baseUrl)
        draftStore.save(
            SetupSection.STORAGE,
            mapOf(
                "FILE_STORAGE_ROOT" to result.root.toString(),
                "FILE_BASE_URL" to result.baseUrl,
            ),
        )
        val rs = Response(result.root.toString(), result.baseUrl)
        return responseBuilder.ok().data(rs).build()
    }
    
    
    @PostMapping("/sms/verify")
            /**
             * verifySms 函数：执行与该组件职责相关的业务操作。
             * 参数和返回值遵循调用方与领域服务之间的约定。
             */
            /**
             * verifySms 的职责与行为说明。
             * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
             */
    fun verifySms(
        @RequestParam(name = "access_key_id") accessKeyId: String,
        @RequestParam(name = "access_key_secret") accessKeySecret: String,
        @RequestParam(name = "sign_name") signName: String,
        @RequestParam(name = "template_code") templateCode: String,
        @RequestParam(name = "phone") phone: String,
        @RequestParam(name = "endpoint", defaultValue = "") endpoint: String,
    ): ResponseEntity<Response> {
        /**
         * Response 的职责与行为说明。
         * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
         */
        data class Response(
            @param:JsonProperty("phone") val phone: String,
            @param:JsonProperty("code") val code: String,
        )
        
        val result = smsProbe.probe(accessKeyId, accessKeySecret, endpoint, signName, templateCode, phone)
        draftStore.save(
            SetupSection.SMS,
            mapOf(
                "SMS_ENABLED" to "true",
                "SMS_ACCESS_KEY_ID" to accessKeyId.trim(),
                "SMS_ACCESS_KEY_SECRET" to accessKeySecret.trim(),
                "SMS_SIGN_NAME" to signName.trim(),
                "SMS_TEMPLATE_CODE" to templateCode.trim(),
            ),
        )
        val rs = Response(result.phone, result.code)
        return responseBuilder.ok().data(rs).build()
    }
    
    
    @PostMapping("/sms/skip")
            /**
             * skipSms 函数：执行与该组件职责相关的业务操作。
             * 参数和返回值遵循调用方与领域服务之间的约定。
             */
            /**
             * skipSms 的职责与行为说明。
             * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
             */
    fun skipSms(): ResponseEntity<Response> {
        /**
         * Response 的职责与行为说明。
         * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
         */
        data class Response(
            @param:JsonProperty("enabled") val enabled: Boolean,
        )
        
        draftStore.save(
            SetupSection.SMS,
            mapOf(
                "SMS_ENABLED" to "false",
                "SMS_ACCESS_KEY_ID" to "",
                "SMS_ACCESS_KEY_SECRET" to "",
                "SMS_SIGN_NAME" to "",
                "SMS_TEMPLATE_CODE" to "",
            ),
        )
        return responseBuilder.ok().data(Response(false)).build()
    }
    
    
    @PostMapping("/administrator/save")
            /**
             * saveAdministrator 函数：执行与该组件职责相关的业务操作。
             * 参数和返回值遵循调用方与领域服务之间的约定。
             */
            /**
             * saveAdministrator 的职责与行为说明。
             * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
             */
    fun saveAdministrator(
        @RequestParam(name = "username") username: String,
        @RequestParam(name = "password") password: String,
        @RequestParam(name = "confirm_password") confirmPassword: String,
    ): ResponseEntity<Response> {
        /**
         * Response 的职责与行为说明。
         * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
         */
        data class Response(
            @param:JsonProperty("username") val username: String,
        )
        
        val trimmedUsername = username.trim()
        if (!USERNAME_PATTERN.matches(trimmedUsername)) {
            throw SetupException("用户名只能包含字母、数字、下划线、点和短横线，长度 3-32 位")
        }
        if (password != confirmPassword) {
            throw SetupException("两次输入的密码不一致")
        }
        if (password.length < MIN_PASSWORD_LENGTH) {
            throw SetupException("密码至少 $MIN_PASSWORD_LENGTH 位")
        }
        if (password.length > MAX_PASSWORD_LENGTH) {
            throw SetupException("密码不能超过 $MAX_PASSWORD_LENGTH 位")
        }
        if (password.trim() != password) {
            throw SetupException("密码首尾不能包含空格")
        }
        if (password.equals(trimmedUsername, ignoreCase = true) || password.lowercase() in WEAK_PASSWORDS) {
            throw SetupException("密码过于简单，请换一个")
        }
        val characterClasses = listOf(
            password.any(Char::isLowerCase),
            password.any(Char::isUpperCase),
            password.any(Char::isDigit),
            password.any { !it.isLetterOrDigit() },
        ).count { it }
        if (characterClasses < MIN_CHARACTER_CLASSES) {
            throw SetupException("密码需包含大写字母、小写字母、数字、符号中的至少 $MIN_CHARACTER_CLASSES 类")
        }
        
        draftStore.save(
            SetupSection.ADMINISTRATOR,
            mapOf(
                "ADMIN_INITIALIZER_ENABLED" to "true",
                "ADMIN_INITIALIZER_USERNAME" to trimmedUsername,
                "ADMIN_INITIALIZER_PASSWORD" to password,
                "ADMIN_INITIALIZER_FORCE_WRITE" to "false",
            ),
        )
        return responseBuilder.ok().data(Response(trimmedUsername)).build()
    }
    
    
    @PostMapping("/complete")
            /**
             * complete 函数：执行与该组件职责相关的业务操作。
             * 参数和返回值遵循调用方与领域服务之间的约定。
             */
            /**
             * complete 的职责与行为说明。
             * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
             */
    fun complete(
        @RequestParam(name = "frontend_origin", defaultValue = "") frontendOrigin: String,
    ): ResponseEntity<Response> {
        /**
         * Response 的职责与行为说明。
         * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
         */
        data class Response(
            @param:JsonProperty("env_file") val envFile: String,
            @param:JsonProperty("backup_file") val backupFile: String?,
            @param:JsonProperty("restarting") val restarting: Boolean,
        )
        
        val result = completion.complete(frontendOrigin.trim().ifEmpty { null })
        val rs = Response(
            envFile = result.envPath.toString(),
            backupFile = result.backupPath?.toString(),
            restarting = true,
        )
        return responseBuilder.ok().data(rs).build()
    }
    
    private companion object {
        const val DEFAULT_KEYTOP_BASE_URL = "https://kp-open.keytop.cn/unite-api"
        const val MIN_PASSWORD_LENGTH = 8
        const val MAX_PASSWORD_LENGTH = 64
        const val MIN_CHARACTER_CLASSES = 2
        val USERNAME_PATTERN = Regex("^[A-Za-z0-9._-]{3,32}$")
        val WEAK_PASSWORDS = setOf("admin", "12345678", "password", "admin123", "123456789", "qwerty123")
    }
}





