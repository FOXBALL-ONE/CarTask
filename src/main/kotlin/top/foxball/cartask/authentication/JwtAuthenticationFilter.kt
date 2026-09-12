package top.foxball.cartask.authentication

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.slf4j.MDC
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import top.foxball.cartask.service.OnlinePresenceService
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** 在授权规则执行前完成 JWT 与 Redis 会话的联合认证，并写入当前请求 SecurityContext。 */
@Component
class JwtAuthenticationFilter(
    private val jwtTokenService: JwtTokenService,
    private val sessionRepository: RedisTokenSessionRepository,
    private val rolePermissionService: RolePermissionService,
    private val onlinePresenceService: OnlinePresenceService? = null,
) : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(javaClass)
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
            // 只比对 token 与会话都必须一致的字段。工作部门是纯会话态、刻意不进 JWT，
            // 因此绝不能加入这里的一致性校验，否则切换工作部门会被判成「JWT 与登录状态不匹配」。
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
                userId = verified.userId,
                username = verified.username,
                role = verified.role,
                tokenId = verified.tokenId,
                permissions = rolePermissionService.permissionsFor(verified.role),
                mustChangePassword = session.mustChangePassword,
                workingDepartmentId = session.workingDepartmentId,
            )
            MDC.put("actor_type", "USER")
            MDC.put("actor_id", "user:${principal.userId}")
            MDC.put("actor_role", principal.role)
            val context = SecurityContextHolder.createEmptyContext()
            context.authentication = UsernamePasswordAuthenticationToken(principal, null, principal.authorities).apply {
                details = WebAuthenticationDetailsSource().buildDetails(request)
            }
            SecurityContextHolder.setContext(context)
            runCatching { onlinePresenceService?.touch(principal.userId) }
                .onFailure { log.debug("记录在线心跳失败: {}", it.message) }
            filterChain.doFilter(request, response)
        } catch (ex: AuthenticationInfrastructureException) {
            log.warn("JWT 认证基础设施不可用: {}", ex.message)
            SecurityContextHolder.clearContext()
            writeFailure(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "认证服务暂不可用", true)
        } catch (ex: AuthenticationException) {
            log.warn("JWT 认证失败: {}", ex.message)
            SecurityContextHolder.clearContext()
            writeFailure(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized", false)
        } catch (ex: IllegalArgumentException) {
            // 签名校验通过的 token 里带着白名单之外的角色（例如角色被下线后旧 token 仍在有效期内），
            // SecurityRole.normalize 会抛 IllegalArgumentException。这是认证失败而不是服务端故障，
            // 不兜住就会变成 500。
            log.warn("JWT 角色不受支持: {}", ex.message)
            SecurityContextHolder.clearContext()
            writeFailure(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized", false)
        }
    }
    
    private fun writeFailure(response: HttpServletResponse, status: Int, message: kotlin.String, retryable: Boolean) {
        response.status = status
        response.contentType = "application/json;charset=UTF-8"
        response.setHeader("Cache-Control", "no-store")
        if (retryable) response.setHeader("Retry-After", "1") else response.setHeader("WWW-Authenticate", "Bearer")
        response.writer.write("{\"status\":$status,\"success\":${status in 200..299},\"message\":\"$message\",\"data\":{}}")
    }
}
