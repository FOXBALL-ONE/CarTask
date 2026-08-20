package top.foxball.cartask.authentication

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.slf4j.MDC
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** 在授权规则执行前完成 JWT 与 Redis 会话的联合认证，并写入当前请求 SecurityContext。 */
@Component
class JwtAuthenticationFilter(
    private val jwtTokenService: JwtTokenService,
    private val sessionRepository: RedisTokenSessionRepository,
    private val rolePermissionService: RolePermissionService,
) : OncePerRequestFilter() {
    private val tokenResolver = DefaultBearerTokenResolver()
    
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val bearerValue: AccessTokenValue? = try {
            tokenResolver.resolve(request)
        } catch (_: AuthenticationException) {
            writeFailure(response, HttpServletResponse.SC_UNAUTHORIZED, "Bearer token 格式无效", false)
            return
        }
        if (bearerValue == null) {
            filterChain.doFilter(request, response)
            return
        }
        try {
            val verified = jwtTokenService.verify(bearerValue)
            val session = sessionRepository.validate(verified.tokenId, verified.userId)
            if (session.userId != verified.userId ||
                session.username != verified.username ||
                session.role != verified.role ||
                session.tokenVersion != verified.tokenVersion ||
                !MessageDigest.isEqual(
                    session.tokenHash.toByteArray(StandardCharsets.UTF_8),
                    verified.tokenHash.toByteArray(StandardCharsets.UTF_8),
                )
            ) {
                throw JwtAuthenticationException("JWT 与登录状态不匹配")
            }
            val principal = CurrentUserPrincipal(
                verified.userId,
                verified.username,
                verified.role,
                verified.tokenId,
                rolePermissionService.permissionsFor(verified.role),
            )
            MDC.put("actor_type", "USER")
            MDC.put("actor_id", "user:${principal.userId}")
            MDC.put("actor_role", principal.role)
            val context = SecurityContextHolder.createEmptyContext()
            context.authentication = UsernamePasswordAuthenticationToken(principal, null, principal.authorities).apply {
                details = WebAuthenticationDetailsSource().buildDetails(request)
            }
            SecurityContextHolder.setContext(context)
            filterChain.doFilter(request, response)
        } catch (_: AuthenticationInfrastructureException) {
            SecurityContextHolder.clearContext()
            writeFailure(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "认证服务暂不可用", true)
        } catch (_: AuthenticationException) {
            SecurityContextHolder.clearContext()
            writeFailure(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized", false)
        }
    }
    
    private fun writeFailure(response: HttpServletResponse, status: Int, message: kotlin.String, retryable: Boolean) {
        response.status = status
        response.contentType = "application/json;charset=UTF-8"
        response.setHeader("Cache-Control", "no-store")
        if (retryable) response.setHeader("Retry-After", "1") else response.setHeader("WWW-Authenticate", "Bearer")
        response.writer.write("{\"status\":$status,\"message\":\"$message\",\"data\":{}}")
    }
}
