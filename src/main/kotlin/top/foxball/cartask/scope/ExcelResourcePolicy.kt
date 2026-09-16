package top.foxball.cartask.scope

/**
 * ExcelResourcePolicy 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import top.foxball.cartask.authentication.CurrentUserPrincipal
import top.foxball.cartask.authentication.SecurityRole
import top.foxball.cartask.repository.DepartmentRepository


data class ForcedImportDepartment(val id: Long, val code: String, val name: String)


@Component
/**
 * ExcelResourcePolicy 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
class ExcelResourcePolicy(
    private val scopeGuard: ScopeGuard,
    private val departmentRepository: DepartmentRepository,
) {
    
    private val unscopableResources = setOf("positions", "devices", "departments", "all")
    
    
    /**
     * requireScopable 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun requireScopable(resource: String) {
        if (scopeGuard.currentScope().unrestricted) return
        require(resource !in unscopableResources) {
            "「$resource」没有部门归属信息，无法按当前工作部门范围处理，请先切换到「全部部门」"
        }
    }
    
    
    /**
     * forcedImportDepartment 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun forcedImportDepartment(): ForcedImportDepartment? {
        val scope = scopeGuard.currentScope()
        if (scope.unrestricted) return null
        val departmentId = currentWorkingDepartmentId()
            ?: scope.departmentIds.singleOrNull()
            ?: throw IllegalArgumentException("请先选择一个具体的工作部门再导入")
        if (departmentId !in scope.departmentIds) {
            throw IllegalArgumentException("当前工作部门不在你的管理范围内，请切换工作部门后再导入")
        }
        val department = departmentRepository.findById(departmentId).orElseThrow {
            IllegalArgumentException("当前工作部门不存在: $departmentId")
        }
        return ForcedImportDepartment(departmentId, department.departmentNumber, department.name)
    }
    
    
    /**
     * forcedImportRole 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun forcedImportRole(): String? =
        if (currentPrincipal()?.role == SecurityRole.DEPT_ADMIN) SecurityRole.USER else null
    
    
    private fun currentWorkingDepartmentId(): Long? = currentPrincipal()?.workingDepartmentId
    
    
    private fun currentPrincipal(): CurrentUserPrincipal? =
        SecurityContextHolder.getContext().authentication?.principal as? CurrentUserPrincipal
}


