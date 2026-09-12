package top.foxball.cartask.authentication

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import java.util.Collections

/** 放入 SecurityContext 的当前用户快照，不包含实体、密码或原始 token。 */
class CurrentUserPrincipal(
    val userId: Long,
    val username: String,
    role: String,
    val tokenId: String,
    permissions: Collection<String> = emptySet(),
    /** 为 true 时除改密相关接口外的业务请求都会被拒绝。 */
    val mustChangePassword: Boolean = false,
) {
    val role: String = SecurityRole.normalize(role)
    val permissions: Set<String> = Collections.unmodifiableSet(
        permissions.asSequence().map(SecurityPermission::normalize).toSortedSet(),
    )
    val authorities: List<GrantedAuthority> = buildList {
        add(SimpleGrantedAuthority(SecurityRole.authority(this@CurrentUserPrincipal.role)))
        addAll(this@CurrentUserPrincipal.permissions.map(::SimpleGrantedAuthority))
    }
}
