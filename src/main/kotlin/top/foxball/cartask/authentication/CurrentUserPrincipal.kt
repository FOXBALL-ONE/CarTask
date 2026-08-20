package top.foxball.cartask.authentication

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority

/** 放入 SecurityContext 的当前用户快照，不包含实体、密码或原始 token。 */
data class CurrentUserPrincipal(
    val userId: Long,
    val username: String,
    val role: String,
    val tokenId: String,
) {
    val authorities: Collection<GrantedAuthority>
        get() = listOf(SimpleGrantedAuthority(SecurityRole.authority(role)))
}
