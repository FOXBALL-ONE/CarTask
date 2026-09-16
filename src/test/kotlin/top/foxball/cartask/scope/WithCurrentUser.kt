package top.foxball.cartask.scope

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextImpl
import org.springframework.security.test.context.support.WithSecurityContext
import org.springframework.security.test.context.support.WithSecurityContextFactory
import top.foxball.cartask.authentication.CurrentUserPrincipal








@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@WithSecurityContext(factory = WithCurrentUserSecurityContextFactory::class)
annotation class WithCurrentUser(
    val userId: Long = 1L,
    val username: String = "test-user",
    val role: String = "SUPER_ADMIN",
    
    val authorities: Array<String> = [],
    
    val workingDepartmentId: Long = NO_WORKING_DEPARTMENT,
) {
    companion object {
        
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
