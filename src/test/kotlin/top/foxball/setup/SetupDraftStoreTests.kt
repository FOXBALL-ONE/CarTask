package top.foxball.setup

import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import top.foxball.cartask.config.DotenvLoader

class SetupDraftStoreTests {
    private val objectMapper: ObjectMapper = JsonMapper.builder()
        .addModule(KotlinModule.Builder().build())
        .build()

    private var previousEnvFile: String? = null

    /**
     * 把「既有配置」指到一个不存在的文件上。
     *
     * 草稿在首次读取时会按既有 `.env` 接续；默认路径就是仓库根目录那份开发用的 `.env`，
     * 不隔离的话每个用例都会先被它预填一遍，断言就成了在测开发机上的配置。
     */
    @BeforeTest
    fun isolateExistingConfiguration() {
        previousEnvFile = System.getProperty(DotenvLoader.ENV_FILE_VARIABLE)
        System.setProperty(
            DotenvLoader.ENV_FILE_VARIABLE,
            createTempDirectory("setup-no-env").resolve(".env").toString(),
        )
    }

    @AfterTest
    fun restoreExistingConfiguration() {
        val previous = previousEnvFile
        if (previous == null) {
            System.clearProperty(DotenvLoader.ENV_FILE_VARIABLE)
        } else {
            System.setProperty(DotenvLoader.ENV_FILE_VARIABLE, previous)
        }
    }

    @Test
    fun `保存一步只覆盖属于它的键`() {
        val store = store()

        store.save(SetupSection.DATABASE, mapOf("DB_URL" to "jdbc:postgresql://a", "DB_USERNAME" to "u"))
        store.save(SetupSection.REDIS, mapOf("REDIS_HOST" to "b", "REDIS_PORT" to "6379"))

        val draft = store.read()
        assertEquals("jdbc:postgresql://a", draft.values["DB_URL"])
        assertEquals("b", draft.values["REDIS_HOST"])
        assertEquals(listOf("database", "redis"), draft.steps)
    }

    @Test
    fun `重填一步不会丢掉别的步骤已验证的值`() {
        val store = store()
        store.save(SetupSection.REDIS, mapOf("REDIS_HOST" to "old", "REDIS_PORT" to "6379"))
        store.save(SetupSection.DATABASE, mapOf("DB_URL" to "jdbc:postgresql://a"))

        store.save(SetupSection.REDIS, mapOf("REDIS_HOST" to "new", "REDIS_PORT" to "6380"))

        val draft = store.read()
        assertEquals("new", draft.values["REDIS_HOST"])
        assertEquals("jdbc:postgresql://a", draft.values["DB_URL"], "改 Redis 不该让数据库那一步失效")
        assertEquals(listOf("redis", "database"), draft.steps)
    }

    @Test
    fun `草稿落盘后能被重新读出来`() {
        val directory = createTempDirectory("setup-draft")
        val draftFile = directory.resolve("draft.json")
        val properties = SetupProperties(draftFile = draftFile.toString())

        SetupDraftStore(properties, objectMapper)
            .save(SetupSection.KEYTOP, mapOf("KEYTOP_APP_ID" to "12250", "KEYTOP_APP_SECRET" to "s"))
        val reader = SetupDraftStore(properties, objectMapper)

        assertTrue(Files.exists(draftFile))
        val draft = reader.read()
        assertEquals("12250", draft.values["KEYTOP_APP_ID"])
        assertEquals(listOf("keytop"), draft.steps)
    }

    @Test
    fun `删除草稿后回到空状态`() {
        val store = store()
        store.save(SetupSection.DATABASE, mapOf("DB_URL" to "jdbc:postgresql://a"))

        store.delete()

        assertEquals(SetupDraft(), store.read())
    }

    @Test
    fun `既有配置能带出已完成的步骤`() {
        val seeded = SetupDraftStore.seedFrom(
            mapOf(
                "DB_URL" to "jdbc:postgresql://192.168.1.95:5432/cartask",
                "DB_USERNAME" to "cartask",
                "DB_PASSWORD" to "pwd",
                "REDIS_HOST" to "192.168.1.95",
                "REDIS_PORT" to "6379",
                "FILE_STORAGE_ROOT" to "./st",
                "FILE_BASE_URL" to "http://192.168.1.95:8080",
            ),
        )

        assertEquals(listOf("database", "redis", "storage"), seeded.steps)
        assertEquals("./st", seeded.values["FILE_STORAGE_ROOT"])
    }

    @Test
    fun `存储只配了目录不算完成`() {
        // 下载基址缺了的话附件链接拼不出来，这一步放进「已完成」会让问题拖到用户点下载才暴露。
        val seeded = SetupDraftStore.seedFrom(mapOf("FILE_STORAGE_ROOT" to "./st"))

        assertEquals(emptyList(), seeded.steps)
    }

    @Test
    fun `没填全的步骤不算已完成`() {
        val seeded = SetupDraftStore.seedFrom(mapOf("DB_URL" to "jdbc:postgresql://192.168.1.95:5432/cartask"))

        assertEquals(emptyList(), seeded.steps, "缺用户名的数据库步骤不该被当成配好了")
    }

    @Test
    fun `没有必填项的步骤只有配置里出现过才算完成`() {
        // 短信整步可跳过，因此 satisfiedBy 永远为真；只看它会让重新进入引导时这一步凭空打上勾。
        assertEquals(emptyList(), SetupDraftStore.seedFrom(mapOf("REDIS_HOST" to "a")).steps)

        val configured = SetupDraftStore.seedFrom(mapOf("SMS_ENABLED" to "false"))
        assertEquals(listOf("sms"), configured.steps)
    }

    @Test
    fun `与引导无关的键不进入草稿`() {
        val seeded = SetupDraftStore.seedFrom(
            mapOf("DB_URL" to "jdbc:postgresql://a", "JWT_SIGNING_KEY_LOCAL" to "keep-out"),
        )

        assertTrue("JWT_SIGNING_KEY_LOCAL" !in seeded.values)
    }

    private fun store(): SetupDraftStore {
        val directory = createTempDirectory("setup-draft")
        return SetupDraftStore(
            SetupProperties(draftFile = directory.resolve("draft.json").toString()),
            objectMapper,
        )
    }
}
