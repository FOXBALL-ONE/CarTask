package top.foxball.cartask.scope

import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Component
import top.foxball.cartask.entity.ParkingPlate
import top.foxball.cartask.entity.ParkingSpot

/** 客户端请求的部门过滤参数与当前范围求交后的结果。 */
data class DepartmentFilter(
    /** 为 true 时表示请求的部门不在范围内，调用方必须返回空结果而不是照它过滤。 */
    val denied: Boolean,
    /** 与范围求交后真正生效的部门过滤值；null 表示不加部门过滤。 */
    val departmentId: Long?,
)

/**
 * 详情与写路径的范围校验。
 *
 * 列表接口靠 [scoped] / [ScopeQuerySupport] 过滤结果集，但按 ID 取详情、更新、删除这些路径
 * 不经过列表，必须单独校验，否则可以直接用 ID 越权读到范围外的数据。
 */
@Component
class ScopeGuard(
    private val dataScopeResolver: DataScopeResolver,
    private val departmentLinkResolver: DepartmentLinkResolver,
    private val scopeQuerySupport: ScopeQuerySupport,
) {
    /**
     * currentScope：查询或读取相关数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun currentScope(): DataScope = dataScopeResolver.current()

    /** 车牌与车位没有自己的部门字段，归属要经车主判定，因此单独给两个入口。 */
    fun requireVisiblePlate(row: ParkingPlate?, scope: DataScope, notFoundMessage: String): ParkingPlate {
        if (row == null || !scopeQuerySupport.plateVisible(visibleOwnerIds(scope), scope.userId, row)) {
            throw IllegalArgumentException(notFoundMessage)
        }
        return row
    }

    /**
     * requireVisibleSpot：校验输入、状态或访问条件。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param row 参与本次处理的输入参数。
     * @param scope 参与本次处理的输入参数。
     * @param notFoundMessage 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun requireVisibleSpot(row: ParkingSpot?, scope: DataScope, notFoundMessage: String): ParkingSpot {
        if (row == null || !scopeQuerySupport.spotVisible(visibleOwnerCodes(scope), row.ownerCode)) {
            throw IllegalArgumentException(notFoundMessage)
        }
        return row
    }

    /** null 表示不受限，避免逐行重算。 */
    private fun visibleOwnerIds(scope: DataScope): Set<Long>? =
        if (scope.unrestricted) null else scopeQuerySupport.ownerIdsInScope(scope)

    /**
     * visibleOwnerCodes：执行当前模块中的业务操作。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param scope 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    private fun visibleOwnerCodes(scope: DataScope): Set<String>? =
        if (scope.unrestricted) null else scopeQuerySupport.ownerCardIdsInScope(scope)

    /**
     * 目标行不在范围内时抛 [AccessDeniedException]。
     *
     * 本人范围直接拒绝：本方法服务的是主数据管理场景，普通用户没有这些接口的权限；
     * 若将来有接口需要让普通用户读自己的某条主数据，应当为此写专门的谓词而不是放开这里。
     */
    fun <T : DepartmentScoped> requireVisible(row: T, scope: DataScope) {
        if (!visibleInScope(row, scope)) throw AccessDeniedException("无权访问其他部门的数据")
    }

    /**
     * 按 ID 取详情、更新、删除时的范围校验入口。
     *
     * 传 null（不存在）与传范围外的行走同一个 [IllegalArgumentException]：详情、更新、删除这些
     * 按 ID 的接口不经过列表过滤，如果只靠 @PreAuthorize 放行，部门管理拿到别部门的 ID 就能读写。
     * 用同一个错误语义是为了避免通过响应差异探测其他部门是否存在该对象。
     */
    fun <T : DepartmentScoped> requireVisibleRow(row: T?, scope: DataScope, notFoundMessage: String): T {
        if (row == null || !visibleInScope(row, scope)) throw IllegalArgumentException(notFoundMessage)
        return row
    }

    private fun <T : DepartmentScoped> visibleInScope(row: T, scope: DataScope): Boolean = when (scope.kind) {
        ScopeKind.ALL -> true
        ScopeKind.SELF -> false
        ScopeKind.DEPARTMENTS -> {
            val code = row.scopeDeptCode ?: departmentLinkResolver.snapshot().toCode(row.scopeDeptFreeText)
            code != null && code in scope.departmentCodes
        }
    }

    /**
     * 把客户端传来的部门过滤参数与当前范围求交。
     *
     * 这是读取该参数的唯一入口。写成「传了就用传的，没传就用范围」是一行数据泄露，
     * 而且代码读起来完全正确——部门管理只要把 department_id 换成别的部门就能越权读取。
     */
    fun requestedDepartment(requested: Long?, scope: DataScope): DepartmentFilter = when {
        requested == null -> DepartmentFilter(denied = false, departmentId = null)
        scope.unrestricted -> DepartmentFilter(denied = false, departmentId = requested)
        requested in scope.departmentIds -> DepartmentFilter(denied = false, departmentId = requested)
        else -> DepartmentFilter(denied = true, departmentId = requested)
    }

    /** 写路径：目标部门必须在范围内，否则拒绝。 */
    fun requireDepartmentAllowed(departmentId: Long?, scope: DataScope) {
        if (departmentId == null) return
        if (scope.unrestricted) return
        if (departmentId !in scope.departmentIds) throw AccessDeniedException("无权操作其他部门的数据")
    }

    /**
     * 写路径：目标用户必须在当前范围内，否则拒绝。
     *
     * 用户表的范围过滤只下推到了列表查询（见 UserServiceImpl.list），而按 ID 更新不经过列表，
     * 部门管理只要换一个用户 ID 就能改掉别部门账号的手机号——而手机号是短信登录与重置密码的凭据。
     *
     * 没有部门的账号在受限范围下一律拒绝：范围解析不出归属时的失败语义是「看不到」，
     * 这里保持一致，否则一个部门归属为空的账号就成了任何部门管理都能改的口子。
     */
    fun requireUserInScope(departmentId: Long?, scope: DataScope) {
        when (scope.kind) {
            ScopeKind.ALL -> return
            ScopeKind.SELF -> throw AccessDeniedException("无权操作其他用户的账号")
            ScopeKind.DEPARTMENTS -> if (departmentId == null || departmentId !in scope.departmentIds) {
                throw AccessDeniedException("无权操作其他部门的用户")
            }
        }
    }

    /**
     * 写路径：目标部门编码必须在范围内，否则拒绝。
     *
     * 新建数据时必须校验：否则部门管理可以造出一条自己看不见、但属于别的部门的记录，
     * 或者在更新时把本部门的数据挪到范围外。
     */
    fun requireDepartmentCodeAllowed(departmentCode: String?, scope: DataScope) {
        if (scope.unrestricted) return
        if (departmentCode == null || departmentCode !in scope.departmentCodes) {
            throw AccessDeniedException("无权在其它部门下操作数据")
        }
    }

    /** 写路径：车牌的目标车主必须在范围内。 */
    fun requireOwnerAllowed(ownerId: Long?, scope: DataScope) {
        if (scope.unrestricted) return
        if (ownerId == null || ownerId !in scopeQuerySupport.ownerIdsInScope(scope)) {
            throw AccessDeniedException("无权操作其它部门的车主数据")
        }
    }

    /** 写路径：车位的目标车主必须在范围内。 */
    fun requireOwnerCodeAllowed(ownerCode: String?, scope: DataScope) {
        if (scope.unrestricted) return
        if (ownerCode == null || ownerCode !in scopeQuerySupport.ownerCardIdsInScope(scope)) {
            throw AccessDeniedException("无权操作其它部门的车主数据")
        }
    }
}
