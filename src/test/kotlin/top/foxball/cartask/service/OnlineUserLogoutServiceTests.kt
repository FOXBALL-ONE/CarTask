package top.foxball.cartask.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import top.foxball.cartask.audit.AuditAction
import top.foxball.cartask.audit.AuditCommand
import top.foxball.cartask.audit.AuditService
import top.foxball.cartask.authentication.RedisTokenSessionRepository
import top.foxball.cartask.entity.User
import top.foxball.cartask.repository.UserRepository

/**
 * 强制登出的核心断言：撤销靠 token version 自增（该用户所有已签发的 token 一起失效），
 * 在线标记单独摘掉，查不到的账号只跳过不报错。
 */
class OnlineUserLogoutServiceTests {
    private val userRepository = mock<UserRepository>()
    private val tokenSessionRepository = mock<RedisTokenSessionRepository>()
    private val onlinePresenceService = mock<OnlinePresenceService>()
    private val auditService = mock<AuditService>()
    private val service = OnlineUserLogoutService(
        userRepository,
        tokenSessionRepository,
        onlinePresenceService,
        auditService,
    )


    private fun storedUser(id: Long, username: String) = User().apply {
        this.id = id
        this.username = username
    }


    @Test
    fun `强制登出会撤销会话并摘掉在线标记`() {
        whenever(userRepository.findAllById(listOf(7L))).thenReturn(listOf(storedUser(7L, "zhangsan")))

        val result = service.forceLogout(listOf(7L))

        assertEquals(listOf(7L), result.loggedOut)
        assertTrue(result.skipped.isEmpty())
        verify(tokenSessionRepository).incrementTokenVersion(7L)
        verify(onlinePresenceService).remove(listOf(7L))
    }


    @Test
    fun `强制登出写审计并带上被踢的账号`() {
        whenever(userRepository.findAllById(listOf(7L))).thenReturn(listOf(storedUser(7L, "zhangsan")))

        service.forceLogout(listOf(7L))

        val captor = argumentCaptor<AuditCommand>()
        verify(auditService).record(captor.capture())
        assertEquals(AuditAction.AUTH_FORCED_LOGOUT, captor.firstValue.action)
        assertEquals("zhangsan", captor.firstValue.targetId)
    }


    @Test
    fun `查不到的账号只跳过不撤销`() {
        whenever(userRepository.findAllById(listOf(7L, 404L))).thenReturn(listOf(storedUser(7L, "zhangsan")))

        val result = service.forceLogout(listOf(7L, 404L))

        assertEquals(listOf(7L), result.loggedOut)
        assertEquals(listOf(404L), result.skipped)
        verify(tokenSessionRepository, never()).incrementTokenVersion(404L)
    }


    @Test
    fun `重复提交同一个账号只撤销一次`() {
        whenever(userRepository.findAllById(listOf(7L))).thenReturn(listOf(storedUser(7L, "zhangsan")))

        val result = service.forceLogout(listOf(7L, 7L, 7L))

        assertEquals(listOf(7L), result.loggedOut)
        verify(tokenSessionRepository).incrementTokenVersion(7L)
    }


    @Test
    fun `审计写失败不影响已经生效的登出`() {
        whenever(userRepository.findAllById(listOf(7L))).thenReturn(listOf(storedUser(7L, "zhangsan")))
        whenever(auditService.record(any())).thenThrow(IllegalStateException("审计库不可用"))

        val result = service.forceLogout(listOf(7L))

        assertEquals(listOf(7L), result.loggedOut)
        verify(tokenSessionRepository).incrementTokenVersion(7L)
    }


    @Test
    fun `空名单被拒绝`() {
        assertThrows(IllegalArgumentException::class.java) { service.forceLogout(emptyList()) }
    }


    @Test
    fun `超过单次上限被拒绝`() {
        assertThrows(IllegalArgumentException::class.java) { service.forceLogout((1L..201L).toList()) }
    }
}
