package top.foxball.cartask.authentication

import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import top.foxball.cartask.repository.RoleRepository

/**
 * 查询当前角色的启用权限，用于在每个有效 JWT 请求中构建 Spring Security authorities。
 *
 * 角色记录缺失或被禁用时必须拒绝认证，避免仅凭角色 authority 绕过角色配置治理。
 */
@Service
class RolePermissionService(
    private val roleRepository: RoleRepository,
) {
    @Transactional(readOnly = true)
    fun permissionsFor(role: String): Set<String> = try {
        val configuredRole = roleRepository.findByNameIgnoreCase(SecurityRole.normalize(role))
        if (configuredRole != null && !configuredRole.enabled) {
            throw JwtAuthenticationException("用户角色已禁用")
        }
        val role = configuredRole ?: throw JwtAuthenticationException("用户角色未配置")
        val permissions = role.permissions
            .asSequence()
            .filter { it.enabled }
            .map { permission ->
                try {
                    SecurityPermission.normalize(permission.code)
                } catch (ex: IllegalArgumentException) {
                    throw AuthenticationInfrastructureException("角色权限配置无效", ex)
                }
            }
            .toList()
        if (permissions.distinct().size != permissions.size) {
            throw AuthenticationInfrastructureException("角色权限配置重复")
        }
        permissions.toSortedSet()
    } catch (ex: DataAccessException) {
        throw AuthenticationInfrastructureException("读取角色权限失败", ex)
    }
}
