package top.foxball.cartask.authentication

/**
 * RolePermissionService：认证子系统中的组件，负责实现相关安全、令牌或访问控制能力。
 *
 * 该文件中的类型和函数用于支撑登录认证流程，并在边界处校验输入与会话状态。
 */

/**
 * RolePermissionService 认证组件说明。
 *
 * 该文件集中定义认证流程所需的领域类型、服务及基础设施适配逻辑。
 */

import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import top.foxball.cartask.repository.RoleRepository


@Service
/**
 * RolePermissionService 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
class RolePermissionService(
    private val roleRepository: RoleRepository,
) {
    @Transactional(readOnly = true)
            
            
            /**
             * permissionsFor 函数：执行与该组件职责相关的业务操作。
             * 参数和返回值遵循调用方与领域服务之间的约定。
             */
            /** permissionsFor：执行认证组件中的一项具体操作，完成输入校验并返回处理结果。 */
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


