package top.foxball.cartask.authentication

import jakarta.servlet.FilterChain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import java.time.LocalDateTime

class JwtAuthenticationFilterTests {
    private val jwtTokenService = mock<JwtTokenService>()
    private val sessionRepository = mock<RedisTokenSessionRepository>()
    private val filter = JwtAuthenticationFilter(jwtTokenService, sessionRepository)
    private val chain = mock<FilterChain>()
    
    @AfterEach
    fun clearContext() {
        SecurityContextHolder.clearContext()
    }
    
    @Test
    fun `有效token写入SecurityContext并继续过滤器链`() {
        val request = MockHttpServletRequest().apply {
            addHeader("Authorization", "Bearer valid-token")
        }
        val response = MockHttpServletResponse()
        whenever(jwtTokenService.verify("valid-token"))
            .thenReturn(JwtTokenService.VerifiedToken("jti-1", 7, "alice", "ADMIN", 2, "hash"))
        whenever(sessionRepository.validate("jti-1", 7)).thenReturn(
            RedisTokenSession(
                7, "alice", "ADMIN", 2, "hash", "cipher", "storage-key", 1,
                LocalDateTime.parse("2026-08-19T10:00:00"),
                LocalDateTime.parse("2026-08-19T12:00:00"),
            ),
        )
        
        filter.doFilter(request, response, chain)
        
        val authentication = SecurityContextHolder.getContext().authentication
        assertNotNull(authentication)
        requireNotNull(authentication)
        val principal = authentication.principal as CurrentUserPrincipal
        assertEquals(7, principal.userId)
        assertEquals("jti-1", principal.tokenId)
        assertTrue(authentication.authorities.any { it.authority == "ROLE_ADMIN" })
        verify(chain).doFilter(request, response)
    }
    
    @Test
    fun `Redis不可用返回503且不继续链`() {
        val request = MockHttpServletRequest().apply {
            addHeader("Authorization", "Bearer valid-token")
        }
        val response = MockHttpServletResponse()
        whenever(jwtTokenService.verify("valid-token"))
            .thenReturn(JwtTokenService.VerifiedToken("jti-1", 7, "alice", "ADMIN", 2, "hash"))
        whenever(sessionRepository.validate("jti-1", 7))
            .thenThrow(AuthenticationInfrastructureException("redis unavailable"))
        
        filter.doFilter(request, response, chain)
        
        assertEquals(503, response.status)
        assertEquals("1", response.getHeader("Retry-After"))
        assertNull(SecurityContextHolder.getContext().authentication)
    }
    
    @Test
    fun `非法Bearer头返回401`() {
        val request = MockHttpServletRequest().apply {
            addHeader("Authorization", "Bearer first, Bearer second")
        }
        val response = MockHttpServletResponse()
        
        filter.doFilter(request, response, chain)
        
        assertEquals(401, response.status)
        assertEquals("Bearer", response.getHeader("WWW-Authenticate"))
        assertNull(SecurityContextHolder.getContext().authentication)
    }
}
