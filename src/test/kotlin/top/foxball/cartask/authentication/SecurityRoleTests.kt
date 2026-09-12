package top.foxball.cartask.authentication

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
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
    fun `部门管理角色属于受支持角色`() {
        assertEquals("DEPT_ADMIN", SecurityRole.normalize(" role_dept_admin "))
        assertEquals("ROLE_DEPT_ADMIN", SecurityRole.authority("DEPT_ADMIN"))
    }

    @Test
    fun `管理类角色集合只包含三个后台角色`() {
        assertEquals(setOf("SUPER_ADMIN", "ADMIN", "DEPT_ADMIN"), SecurityRole.ADMIN_ROLES)
    }

    @Test
    fun `角色优先级按超管平台部门普通递减`() {
        val superAdmin = SecurityRole.priorityOf("SUPER_ADMIN")
        val admin = SecurityRole.priorityOf("ADMIN")
        val deptAdmin = SecurityRole.priorityOf("DEPT_ADMIN")
        val user = SecurityRole.priorityOf("USER")

        assertTrue(superAdmin > admin)
        assertTrue(admin > deptAdmin)
        assertTrue(deptAdmin > user)
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
