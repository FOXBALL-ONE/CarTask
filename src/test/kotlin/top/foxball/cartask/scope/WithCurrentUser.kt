package top.foxball.cartask.scope

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextImpl
import org.springframework.security.test.context.support.WithSecurityContext
import org.springframework.security.test.context.support.WithSecurityContextFactory
import top.foxball.cartask.authentication.CurrentUserPrincipal

/**
 * 以真实的 [CurrentUserPrincipal] 建立测试安全上下文。
 *
 * 不用 `@WithMockUser`：它放入的是 Spring Security 自带的 User，而数据范围解析**刻意**只认
 * [CurrentUserPrincipal]，拿不到就直接抛异常（fail closed，避免后台任务静默绕过范围）。
 * 测试要走真实鉴权路径，就必须放真实的 principal。
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@WithSecurityContext(factory = WithCurrentUserSecurityContextFactory::class)
annotation class WithCurrentUser(
    val userId: Long = 1L,
    val username: String = "test-user",
    val role: String = "SUPER_ADMIN",
    /** 除角色 authority 之外的业务权限。 */
    val authorities: Array<String> = [],
    /** 当前工作部门；null 表示不限部门。 */
    val workingDepartmentId: Long = NO_WORKING_DEPARTMENT,
) {
    companion object {
        /** 表示"不限部门"的哨兵值：注解参数不能为 null。 */
        const val NO_WORKING_DEPARTMENT = -1L
    }
}

class WithCurrentUserSecurityContextFactory : WithSecurityContextFactory<WithCurrentUser> {
    override fun createSecurityContext(annotation: WithCurrentUser): SecurityContext {
        val principal = CurrentUserPrincipal(
            userId = annotation.userId,
            username = annotation.username,
            role = annotation.role,
            tokenId = "test-token",
            permissions = annotation.authorities.toSet(),
            workingDepartmentId = annotation.workingDepartmentId
                .takeIf { it != WithCurrentUser.NO_WORKING_DEPARTMENT },
        )
        val authentication = UsernamePasswordAuthenticationToken(principal, null, principal.authorities)
        return SecurityContextImpl(authentication)
    }
}
