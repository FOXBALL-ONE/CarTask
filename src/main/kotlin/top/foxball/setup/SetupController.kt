package top.foxball.setup

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import top.foxball.cartask.shared.Response
import top.foxball.cartask.shared.ResponseBuilder

/**
 * 配置引导接口。只在配置模式下存在——主应用不扫描这个包，也就没有第二处能改配置的入口。
 *
 * 每一步「校验」同时是「保存」：校验用的就是那一组凭据，校验通过的那一刻正是它最值得被记住的时刻。
 * 分成「保存」和「测试」两个动作的话，页面上就会出现「存了但没验过」的状态，而没验过的配置写进
 * `.env` 之后，问题要到服务重启、连不上数据库时才暴露。
 *
 * 方法名统一以 `verify` 结尾的接口都会真连一次外部依赖，页面上的等待是真实耗时，不是装饰。
 */
@RestController
@RequestMapping("/api/setup")
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

    /** 配置模式自检。免口令，前端要凭它决定「进引导页还是进登录页」。 */
    @GetMapping("/status")
    fun status(): ResponseEntity<Response> {
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

    /**
     * 已完成的进度，供刷新页面后接着填。
     *
     * 机密项只回「有没有」，不回值：页面通常是在实施人员的机器上打开的，而这台机器未必只属于他。
     * 需要改的时候重新填一遍就行，那不构成负担。
     */
    @GetMapping("/draft")
    fun draft(): ResponseEntity<Response> {
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

    /** 校验数据库连接串，通过后记为已完成。 */
    @PostMapping("/database/verify")
    fun verifyDatabase(
        @RequestParam(name = "url") url: String,
        @RequestParam(name = "username") username: String,
        @RequestParam(name = "password", defaultValue = "") password: String,
    ): ResponseEntity<Response> {
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

    /** 校验 Redis 连接，通过后记为已完成。 */
    @PostMapping("/redis/verify")
    fun verifyRedis(
        @RequestParam(name = "host") host: String,
        @RequestParam(name = "port", defaultValue = "6379") port: Int,
        @RequestParam(name = "password", defaultValue = "") password: String,
    ): ResponseEntity<Response> {
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

    /** 校验科拓凭据：会用这组凭据真实调用一次平台只读接口。 */
    @PostMapping("/keytop/verify")
    fun verifyKeytop(
        @RequestParam(name = "base_url", defaultValue = "") baseUrl: String,
        @RequestParam(name = "app_id") appId: String,
        @RequestParam(name = "park_id") parkId: String,
        @RequestParam(name = "park_name", defaultValue = "") parkName: String,
        @RequestParam(name = "app_secret") appSecret: String,
    ): ResponseEntity<Response> {
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

    /** 校验存储目录可写、下载基址合法，通过后记为已完成。 */
    @PostMapping("/storage/verify")
    fun verifyStorage(
        @RequestParam(name = "storage_root", defaultValue = "") storageRoot: String,
        @RequestParam(name = "base_url") baseUrl: String,
    ): ResponseEntity<Response> {
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

    /** 校验短信凭据：会向指定手机号真发一条测试短信。 */
    @PostMapping("/sms/verify")
    fun verifySms(
        @RequestParam(name = "access_key_id") accessKeyId: String,
        @RequestParam(name = "access_key_secret") accessKeySecret: String,
        @RequestParam(name = "sign_name") signName: String,
        @RequestParam(name = "template_code") templateCode: String,
        @RequestParam(name = "phone") phone: String,
        @RequestParam(name = "endpoint", defaultValue = "") endpoint: String,
    ): ResponseEntity<Response> {
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

    /** 跳过短信：写入关闭状态，让这一步不再是「未完成」。 */
    @PostMapping("/sms/skip")
    fun skipSms(): ResponseEntity<Response> {
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

    /**
     * 保存管理员账号。
     *
     * 这里只做强度校验，不建账号：真正创建账号的是主应用启动时的 `AdminInitializer`，它在角色行
     * 建好之后、权限字典补齐之前运行，这个次序是全新库里唯一能拿到治理权限的位置。引导阶段自己插一条
     * 用户记录，等于把那个次序在外面对齐一遍，迟早会对不齐。
     */
    @PostMapping("/administrator/save")
    fun saveAdministrator(
        @RequestParam(name = "username") username: String,
        @RequestParam(name = "password") password: String,
        @RequestParam(name = "confirm_password") confirmPassword: String,
    ): ResponseEntity<Response> {
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
                // 首次启动建号即可；持续覆盖密码会让「改了密码」在下一次重启时被悄悄改回去。
                "ADMIN_INITIALIZER_FORCE_WRITE" to "false",
            ),
        )
        return responseBuilder.ok().data(Response(trimmedUsername)).build()
    }

    /**
     * 写出配置并重启。
     *
     * 返回体里带上备份文件名：覆盖的是上一次生效的配置，出问题时实施人员得知道去哪儿把它找回来。
     */
    @PostMapping("/complete")
    fun complete(
        @RequestParam(name = "frontend_origin", defaultValue = "") frontendOrigin: String,
    ): ResponseEntity<Response> {
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
