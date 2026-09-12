package top.foxball.cartask.authentication

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder

class RoleAssignmentPolicyTests {
    private val policy = RoleAssignmentPolicy()

    @AfterEach
    fun clearContext() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `普通管理员只能授予普通用户角色`() {
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
            "admin",
            null,
            listOf(SimpleGrantedAuthority("ROLE_ADMIN")),
        )

        assertDoesNotThrow { policy.validateAssignment("USER") }
        assertThrows(AccessDeniedException::class.java) { policy.validateAssignment("ADMIN") }
        assertThrows(AccessDeniedException::class.java) { policy.validateAssignment("SUPER_ADMIN") }
        assertThrows(AccessDeniedException::class.java) { policy.validateManagement(listOf("USER", "ADMIN")) }
    }

    @Test
    fun `超级管理员可以授予管理员角色`() {
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
            "root",
            null,
            listOf(SimpleGrantedAuthority("ROLE_SUPER_ADMIN")),
        )

        assertDoesNotThrow { policy.validateAssignment("ADMIN") }
        assertDoesNotThrow { policy.validateAssignment("SUPER_ADMIN") }
        assertDoesNotThrow { policy.validateManagement(listOf("ADMIN", "SUPER_ADMIN")) }
    }

    @Test
    fun `部门管理不能授予任何后台角色`() {
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
            "dept",
            null,
            listOf(SimpleGrantedAuthority("ROLE_DEPT_ADMIN")),
        )

        assertDoesNotThrow { policy.validateAssignment("USER") }
        assertThrows(AccessDeniedException::class.java) { policy.validateAssignment("DEPT_ADMIN") }
        assertThrows(AccessDeniedException::class.java) { policy.validateAssignment("ADMIN") }
        assertThrows(AccessDeniedException::class.java) { policy.validateAssignment("SUPER_ADMIN") }
        assertThrows(AccessDeniedException::class.java) { policy.validateManagement(listOf("USER", "DEPT_ADMIN")) }
    }
}
