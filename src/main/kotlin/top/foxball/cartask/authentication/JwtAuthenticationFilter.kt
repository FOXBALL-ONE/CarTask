package top.foxball.cartask.authentication

/**
 * JwtAuthenticationFilter：认证子系统中的组件，负责实现相关安全、令牌或访问控制能力。
 *
 * 该文件中的类型和函数用于支撑登录认证流程，并在边界处校验输入与会话状态。
 */

/**
 * JwtAuthenticationFilter 认证组件说明。
 *
 * 该文件集中定义认证流程所需的领域类型、服务及基础设施适配逻辑。
 */

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import top.foxball.cartask.service.OnlinePresenceService
import java.nio.charset.StandardCharsets
import java.security.MessageDigest


@Component
/**
 * JwtAuthenticationFilter 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
class JwtAuthenticationFilter(
    private val jwtTokenService: JwtTokenService,
    private val sessionRepository: RedisTokenSessionRepository,
    private val rolePermissionService: RolePermissionService,
    private val onlinePresenceService: OnlinePresenceService? = null,
) : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(javaClass)
    private val tokenResolver = DefaultBearerTokenResolver()
    
    
    /** doFilterInternal：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
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
            log.warn("JWT 角色不受支持: {}", ex.message)
            SecurityContextHolder.clearContext()
            writeFailure(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized", false)
        }
    }
    
    
    /** writeFailure：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
    private fun writeFailure(response: HttpServletResponse, status: Int, message: String, retryable: Boolean) {
        response.status = status
        response.contentType = "application/json;charset=UTF-8"
        response.setHeader("Cache-Control", "no-store")
        if (retryable) response.setHeader("Retry-After", "1") else response.setHeader("WWW-Authenticate", "Bearer")
        response.writer.write("{\"status\":$status,\"success\":${status in 200..299},\"message\":\"$message\",\"data\":{}}")
    }
}


