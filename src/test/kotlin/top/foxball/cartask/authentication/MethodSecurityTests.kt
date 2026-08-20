package top.foxball.cartask.authentication

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import top.foxball.cartask.repository.RoleRepository
import top.foxball.cartask.repository.UserRepository
import top.foxball.cartask.authentication.RedisTokenSessionRepository
import top.foxball.cartask.service.RoleService
import top.foxball.cartask.service.impl.RoleServiceImpl

@SpringJUnitConfig(MethodSecurityTests.TestConfig::class)
class MethodSecurityTests {
    @Autowired
    private lateinit var protectedService: ProtectedService

    @Autowired
    private lateinit var roleService: RoleService

    @Autowired
    private lateinit var roleRepository: RoleRepository

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `hasRole校验通过拥有角色的用户`() {
        assertEquals("admin", protectedService.adminOnly())
    }

    @Test
    @WithMockUser(authorities = ["user:read"])
    fun `hasAuthority校验通过拥有权限的用户`() {
        assertEquals("read", protectedService.readUsers())
    }

    @Test
    @WithMockUser(roles = ["USER"])
    fun `缺少角色或权限时拒绝调用`() {
        assertThrows(AccessDeniedException::class.java) {
            protectedService.adminOnly()
        }
        assertThrows(AccessDeniedException::class.java) {
            protectedService.readUsers()
        }
    }

    @AfterEach
    fun clearSecurityContext() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `角色服务禁止管理员直接修改角色配置`() {
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
            "admin",
            null,
            listOf(SimpleGrantedAuthority("ROLE_ADMIN"), SimpleGrantedAuthority("role:manage")),
        )
        assertThrows(AccessDeniedException::class.java) {
            roleService.create(top.foxball.cartask.entity.Role().apply { name = "USER" })
        }
        verifyNoInteractions(roleRepository)
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    class TestConfig {
        @Bean
        fun protectedService() = ProtectedService()

        @Bean
        fun roleRepository(): RoleRepository = mock()

        @Bean
        fun userRepository(): UserRepository = mock()

        @Bean
        fun tokenSessionRepository(): RedisTokenSessionRepository = mock()

        @Bean
        fun roleService(
            roleRepository: RoleRepository,
            userRepository: UserRepository,
            tokenSessionRepository: RedisTokenSessionRepository,
        ): RoleService = RoleServiceImpl(roleRepository, userRepository, tokenSessionRepository)
    }

    open class ProtectedService {
        @PreAuthorize("hasRole('ADMIN')")
        open fun adminOnly() = "admin"

        @PreAuthorize("hasAuthority('user:read')")
        open fun readUsers() = "read"
    }
}
