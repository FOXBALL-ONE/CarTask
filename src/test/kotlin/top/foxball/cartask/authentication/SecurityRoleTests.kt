package top.foxball.cartask.authentication

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class SecurityRoleTests {
    @Test
    fun `角色编码统一大小写和前缀`() {
        assertEquals("ADMIN", SecurityRole.normalize(" role_admin "))
        assertEquals("ROLE_CUSTOMER", SecurityRole.authority("customer"))
    }
    
    @Test
    fun `未知角色必须拒绝`() {
        assertNull(SecurityRole.normalizeOrNull("SUPER_ADMIN"))
        assertThrows(IllegalArgumentException::class.java) {
            SecurityRole.normalize("SUPER_ADMIN")
        }
    }
}
