package top.foxball.cartask.authentication

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder

/** 初始密码未修改时，业务接口必须被服务端拦下，只有改密相关接口放行。 */
class PasswordChangeRequiredFilterTests {
    private val filter = PasswordChangeRequiredFilter()

    private fun authenticate(mustChangePassword: Boolean) {
        val principal = CurrentUserPrincipal(
            userId = 7L,
            username = "zhangsan",
            role = "USER",
            tokenId = "token-1",
            permissions = emptySet(),
            mustChangePassword = mustChangePassword,
        )
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(principal, null, principal.authorities)
    }

    private fun run(method: String, path: String): Pair<MockHttpServletResponse, MockFilterChain> {
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()
        filter.doFilter(MockHttpServletRequest(method, path), response, chain)
        return response to chain
    }

    @Test
    fun `待改密的会话访问业务接口返回 403 且不进入后续链路`() {
        authenticate(mustChangePassword = true)

        val (response, chain) = run("GET", "/api/users")

        assertEquals(403, response.status)
        assertEquals("application/json;charset=UTF-8", response.contentType)
        assertEquals(
            """{"status":403,"success":false,"message":"首次登录必须先修改密码","data":{"password_change_required":true}}""",
            response.contentAsString,
        )
        assertNull(chain.request, "被拦截的请求不应继续执行")
    }

    @Test
    fun `待改密时个人中心与会话接口仍然放行`() {
        authenticate(mustChangePassword = true)

        listOf(
            "GET" to "/api/profile",
            "PUT" to "/api/profile/password",
            "PUT" to "/api/profile/avatar",
            "GET" to "/api/auth/session",
            "POST" to "/api/auth/logout",
        ).forEach { (method, path) ->
            val (_, chain) = run(method, path)
            assertNotNull(chain.request, "$method $path 应被放行")
        }
    }

    @Test
    fun `不需要改密的会话正常放行`() {
        authenticate(mustChangePassword = false)

        val (_, chain) = run("GET", "/api/users")

        assertNotNull(chain.request)
    }

    @Test
    fun `未认证请求不归本过滤器处理`() {
        SecurityContextHolder.clearContext()

        val (response, chain) = run("GET", "/api/users")

        assertEquals(200, response.status)
        assertNotNull(chain.request)
    }
}
