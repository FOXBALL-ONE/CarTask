package top.foxball.cartask.scope

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import top.foxball.cartask.authentication.CurrentUserPrincipal
import top.foxball.cartask.authentication.SecurityRole
import top.foxball.cartask.repository.DepartmentRepository

/** 范围受限时导入必须落在的部门。 */
data class ForcedImportDepartment(val id: Long, val code: String, val name: String)

/**
 * Excel 导入导出的数据范围策略。
 *
 * Excel 面向部门管理与平台管理（@PreAuthorize 里的角色门已经限定），但**有权限不等于能看到全部数据**：
 * 导出必须按当前工作部门裁剪，导入必须强制落到当前工作部门，否则部门管理可以把别的部门数据导出去，
 * 或者把本部门数据写成别的部门的。
 */
@Component
class ExcelResourcePolicy(
    private val scopeGuard: ScopeGuard,
    private val departmentRepository: DepartmentRepository,
) {
    /** 没有任何部门归属字段、因此无法按范围裁剪的资源。 */
    private val unscopableResources = setOf("positions", "devices", "departments", "all")

    /**
     * 校验该资源在当前范围下能否导入/导出。
     *
     * 受限范围下直接拒绝无法裁剪的资源，而不是返回一份"看起来被限制了"的全量数据——后者是撒谎。
     */
    fun requireScopable(resource: String) {
        if (scopeGuard.currentScope().unrestricted) return
        require(resource !in unscopableResources) {
            "「$resource」没有部门归属信息，无法按当前工作部门范围处理，请先切换到「全部部门」"
        }
    }

    /**
     * 导入时必须落到的部门；返回 null 表示当前范围不受限、按表格里填的部门走。
     *
     * 范围收窄但没有确定到唯一部门时直接拒绝：导入没法猜用户想把数据写到哪个部门。
     */
    fun forcedImportDepartment(): ForcedImportDepartment? {
        val scope = scopeGuard.currentScope()
        if (scope.unrestricted) return null
        val departmentId = currentWorkingDepartmentId()
            ?: scope.departmentIds.singleOrNull()
            ?: throw IllegalStateException("请先选择一个具体的工作部门再导入")
        val department = departmentRepository.findById(departmentId).orElseThrow {
            IllegalStateException("当前工作部门不存在: $departmentId")
        }
        return ForcedImportDepartment(departmentId, department.departmentNumber, department.name)
    }

    /**
     * 导入用户时必须使用的角色；返回 null 表示沿用表格里的角色编码。
     *
     * 部门管理持有 `user:create`，而 Excel 的「角色编码」列会直接进入用户创建命令——
     * 不强制就是一条把普通用户提成管理员的直接路径。RoleAssignmentPolicy 是另一层保险，两层都要有。
     */
    fun forcedImportRole(): String? =
        if (currentPrincipal()?.role == SecurityRole.DEPT_ADMIN) SecurityRole.USER else null

    private fun currentWorkingDepartmentId(): Long? = currentPrincipal()?.workingDepartmentId

    private fun currentPrincipal(): CurrentUserPrincipal? =
        SecurityContextHolder.getContext().authentication?.principal as? CurrentUserPrincipal
}
