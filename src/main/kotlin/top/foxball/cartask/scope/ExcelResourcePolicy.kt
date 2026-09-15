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
     * 拒绝一律用 [IllegalArgumentException]（映射成 400 并带出消息）而不是 IllegalStateException——
     * 后者没有异常处理器，会落到兜底的 500 且消息为空，用户只看到「Internal Server Error」，
     * 完全不知道该去切换工作部门。
     */
    fun forcedImportDepartment(): ForcedImportDepartment? {
        val scope = scopeGuard.currentScope()
        if (scope.unrestricted) return null
        val departmentId = currentWorkingDepartmentId()
            ?: scope.departmentIds.singleOrNull()
            ?: throw IllegalArgumentException("请先选择一个具体的工作部门再导入")
        // 工作部门是会话里记住的上次选择，不保证还在当前管理范围内（管理员调整分配范围后就会脱节）。
        // 不求交就是一条把数据写到自己看不到、也管不到的部门下的越权路径——单条录入路径的
        // requireDepartmentCodeAllowed 在导入这条路上没有第二道防线。
        if (departmentId !in scope.departmentIds) {
            throw IllegalArgumentException("当前工作部门不在你的管理范围内，请切换工作部门后再导入")
        }
        val department = departmentRepository.findById(departmentId).orElseThrow {
            IllegalArgumentException("当前工作部门不存在: $departmentId")
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

    /**
     * currentWorkingDepartmentId：查询或读取相关数据。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    private fun currentWorkingDepartmentId(): Long? = currentPrincipal()?.workingDepartmentId

    /**
     * currentPrincipal：查询或读取相关数据。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    private fun currentPrincipal(): CurrentUserPrincipal? =
        SecurityContextHolder.getContext().authentication?.principal as? CurrentUserPrincipal
}
