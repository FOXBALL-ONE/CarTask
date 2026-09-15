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
            /**
             * permissionsFor：执行当前模块中的业务操作。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @param role 参与本次处理的输入参数。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
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
