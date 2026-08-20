package top.foxball.cartask.authentication

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.security.core.AuthenticationException
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64

class JwtTokenServiceTests {
    private val signingKey = Base64.getEncoder().encodeToString(ByteArray(32) { (it + 1).toByte() })
    private val encryptionKey = Base64.getEncoder().encodeToString(ByteArray(32) { (it + 33).toByte() })
    private val clock = Clock.fixed(Instant.parse("2026-08-19T10:15:30Z"), ZoneOffset.UTC)
    
    @Test
    fun `签发后可以验签并还原当前用户声明`() {
        val service = service()
        
        val issued = service.issue(42, "alice", "ADMIN", 3)
        val verified = service.verify(issued.accessToken)
        
        assertEquals(42, verified.userId)
        assertEquals("alice", verified.username)
        assertEquals("ADMIN", verified.role)
        assertEquals(3, verified.tokenVersion)
        assertEquals(issued.tokenId, verified.tokenId)
        assertEquals(issued.tokenHash, verified.tokenHash)
        assertEquals(Duration.ofHours(2), Duration.between(issued.issuedAt, issued.expiresAt))
        assertNotEquals(issued.accessToken, issued.tokenCiphertext)
    }
    
    @Test
    fun `篡改JWT后必须拒绝`() {
        val service = service()
        val issued = service.issue(42, "alice", "ADMIN", 0)
        val parts = issued.accessToken.split('.').toMutableList()
        parts[1] = parts[1].dropLast(1) + if (parts[1].last() == 'a') 'b' else 'a'
        
        assertThrows(AuthenticationException::class.java) {
            service.verify(parts.joinToString("."))
        }
    }
    
    @Test
    fun `未知kid必须拒绝`() {
        val service = service()
        val issued = service.issue(42, "alice", "ADMIN", 0)
        val parts = issued.accessToken.split('.').toMutableList()
        val changedHeader = "{\"alg\":\"HS256\",\"kid\":\"unknown\"}"
        parts[0] = Base64.getUrlEncoder().withoutPadding().encodeToString(changedHeader.toByteArray())
        
        assertThrows(AuthenticationException::class.java) {
            service.verify(parts.joinToString("."))
        }
    }
    
    private fun service(): JwtTokenService = JwtTokenService(
        JwtProperties(
            issuer = "carTask-tests",
            audience = "carTask-api-tests",
            activeSigningKeyId = "test-key",
            keys = mapOf("test-key" to signingKey),
            ttl = Duration.ofHours(2),
            clockSkew = Duration.ofSeconds(30),
            tokenStorageEncryptionKey = encryptionKey,
            tokenStorageEncryptionKeyId = "test-storage-key",
        ),
        ObjectMapper(),
        clock,
    )
}
