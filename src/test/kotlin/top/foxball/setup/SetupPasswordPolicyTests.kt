package top.foxball.setup

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import top.foxball.cartask.shared.ResponseBuilder

class SetupPasswordPolicyTests {
    private val draftStore = mock<SetupDraftStore>()
    private val controller = SetupController(
        draftStore = draftStore,
        databaseProbe = mock<DatabaseProbe>(),
        redisProbe = mock<RedisProbe>(),
        keytopProbe = mock<KeytopProbe>(),
        storageProbe = mock<StorageProbe>(),
        smsProbe = mock<SmsProbe>(),
        completion = mock<SetupCompletion>(),
        properties = SetupProperties(),
        responseBuilder = ResponseBuilder(),
    )

    @Test
    fun `管理员配置拒绝只含两类字符的密码`() {
        val error = assertThrows(SetupException::class.java) {
            controller.saveAdministrator("setup.admin", "abcdef12", "abcdef12")
        }

        assertTrue(error.message!!.contains("至少 3 类"))
        verify(draftStore, never()).save(any(), any())
    }

    @Test
    fun `管理员配置接受八位且包含三类字符的密码`() {
        controller.saveAdministrator("setup.admin", "Abcdefg1", "Abcdefg1")

        verify(draftStore).save(
            SetupSection.ADMINISTRATOR,
            mapOf(
                "ADMIN_INITIALIZER_ENABLED" to "true",
                "ADMIN_INITIALIZER_USERNAME" to "setup.admin",
                "ADMIN_INITIALIZER_PASSWORD" to "Abcdefg1",
                "ADMIN_INITIALIZER_FORCE_WRITE" to "false",
            ),
        )
    }

    @Test
    fun `旧草稿中的弱密码不能跳过校验写入配置`() {
        val values = SetupSection.entries.flatMap { it.required }.associateWith { "configured" } +
                ("ADMIN_INITIALIZER_PASSWORD" to "abcdef12")
        whenever(draftStore.read()).thenReturn(SetupDraft(values, SetupSection.entries.map { it.id }))
        val envFile = mock<SetupEnvFile>()
        val restartSignal = mock<SetupRestartSignal>()
        val completion = SetupCompletion(draftStore, envFile, SetupProperties(), restartSignal)

        val error = assertThrows(SetupException::class.java) { completion.complete(null) }

        assertTrue(error.message!!.contains("请重新配置管理员账号"))
        verify(envFile, never()).write(any(), any(), any())
        verify(draftStore, never()).delete()
        verify(restartSignal, never()).request()
    }
}
