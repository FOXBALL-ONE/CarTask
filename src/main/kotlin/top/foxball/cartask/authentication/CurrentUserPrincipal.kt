package top.foxball.cartask.authentication

/**
 * CurrentUserPrincipal：认证子系统中的组件，负责实现相关安全、令牌或访问控制能力。
 *
 * 该文件中的类型和函数用于支撑登录认证流程，并在边界处校验输入与会话状态。
 */

/**
 * CurrentUserPrincipal 认证组件说明。
 *
 * 该文件集中定义认证流程所需的领域类型、服务及基础设施适配逻辑。
 */

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import java.util.*


/**
 * CurrentUserPrincipal 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
class CurrentUserPrincipal(
    val userId: Long,
    val username: String,
    role: String,
    val tokenId: String,
    permissions: Collection<String> = emptySet(),
    
    val mustChangePassword: Boolean = false,
    
    
    val workingDepartmentId: Long? = null,
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


