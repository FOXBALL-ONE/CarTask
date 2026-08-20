package top.foxball.cartask.authentication

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class SecurityPermissionTests {
    @Test
    fun `权限编码统一为小写`() {
        assertEquals("user:read", SecurityPermission.normalize(" User:Read "))
    }

    @Test
    fun `格式非法的权限编码必须拒绝`() {
        assertNull(SecurityPermission.normalizeOrNull("user"))
        assertNull(SecurityPermission.normalizeOrNull("user read"))
        assertNull(SecurityPermission.normalizeOrNull("access:control:review"))
        assertThrows(IllegalArgumentException::class.java) {
            SecurityPermission.normalize("user:read!")
        }
    }
}
