package top.foxball.cartask.authentication

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class SecurityRoleTests {
    @Test
    fun `角色编码统一大小写和前缀`() {
        assertEquals("ADMIN", SecurityRole.normalize(" role_admin "))
        assertEquals("ROLE_USER", SecurityRole.authority("user"))
    }
    
    @Test
    fun `未知角色必须拒绝`() {
        assertNull(SecurityRole.normalizeOrNull("CUSTOMER"))
        assertNull(SecurityRole.normalizeOrNull("OWNER"))
        assertThrows(IllegalArgumentException::class.java) {
            SecurityRole.normalize("OWNER")
        }
    }

    @Test
    fun `超级管理员角色属于受支持角色`() {
        assertEquals("SUPER_ADMIN", SecurityRole.normalize(" role_super_admin "))
        assertEquals("ROLE_SUPER_ADMIN", SecurityRole.authority("SUPER_ADMIN"))
    }

    @Test
    fun `当前用户快照规范化并冻结权限集合`() {
        val source = linkedSetOf("User:Read")
        val principal = CurrentUserPrincipal(7, "alice", " role_admin ", "jti-1", source)
        source.add("user:write")

        assertEquals("ADMIN", principal.role)
        assertEquals(setOf("user:read"), principal.permissions)
        assertThrows(UnsupportedOperationException::class.java) {
            (principal.permissions as MutableSet<String>).add("user:write")
        }
    }
}
