package top.foxball.cartask.scope

/**
 * ScopeGuard 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */

import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Component
import top.foxball.cartask.entity.AccessControl
import top.foxball.cartask.entity.ParkingPlate
import top.foxball.cartask.entity.ParkingSpot


data class DepartmentFilter(
    
    val denied: Boolean,
    
    val departmentId: Long?,
)


@Component
/**
 * ScopeGuard 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
class ScopeGuard(
    private val dataScopeResolver: DataScopeResolver,
    private val departmentLinkResolver: DepartmentLinkResolver,
    private val scopeQuerySupport: ScopeQuerySupport,
) {
    
    
    /**
     * currentScope 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun currentScope(): DataScope = dataScopeResolver.current()
    
    
    /**
     * requireVisiblePlate 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun requireVisiblePlate(row: ParkingPlate?, scope: DataScope, notFoundMessage: String): ParkingPlate {
        if (row == null || !scopeQuerySupport.plateVisible(visibleOwnerIds(scope), scope.userId, row)) {
            throw IllegalArgumentException(notFoundMessage)
        }
        return row
    }
    
    
    /**
     * requireVisibleSpot 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun requireVisibleSpot(row: ParkingSpot?, scope: DataScope, notFoundMessage: String): ParkingSpot {
        if (row == null || !scopeQuerySupport.spotVisible(visibleOwnerCodes(scope), row.ownerCode)) {
            throw IllegalArgumentException(notFoundMessage)
        }
        return row
    }
    
    
    private fun visibleOwnerIds(scope: DataScope): Set<Long>? =
        if (scope.unrestricted) null else scopeQuerySupport.ownerIdsInScope(scope)
    
    
    private fun visibleOwnerCodes(scope: DataScope): Set<String>? =
        if (scope.unrestricted) null else scopeQuerySupport.ownerCardIdsInScope(scope)
    
    
    fun <T : DepartmentScoped> requireVisible(row: T, scope: DataScope) {
        if (!visibleInScope(row, scope)) throw AccessDeniedException("无权访问其他部门的数据")
    }
    
    
    fun <T : DepartmentScoped> requireVisibleRow(row: T?, scope: DataScope, notFoundMessage: String): T {
        if (row == null || !visibleInScope(row, scope)) throw IllegalArgumentException(notFoundMessage)
        return row
    }
    
    
    /**
     * requireVisibleAccessControl 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun requireVisibleAccessControl(row: AccessControl?, scope: DataScope, notFoundMessage: String): AccessControl {
        if (row == null || !accessControlVisible(row, scope)) throw IllegalArgumentException(notFoundMessage)
        return row
    }
    
    
    /**
     * accessControlVisible 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun accessControlVisible(row: AccessControl, scope: DataScope): Boolean = when (scope.kind) {
        ScopeKind.ALL -> true
        ScopeKind.SELF -> false
        ScopeKind.DEPARTMENTS -> row.department?.id?.let { it in scope.departmentIds } == true
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
     * requestedDepartment 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun requestedDepartment(requested: Long?, scope: DataScope): DepartmentFilter = when {
        requested == null -> DepartmentFilter(denied = false, departmentId = null)
        scope.unrestricted -> DepartmentFilter(denied = false, departmentId = requested)
        requested in scope.departmentIds -> DepartmentFilter(denied = false, departmentId = requested)
        else -> DepartmentFilter(denied = true, departmentId = requested)
    }
    
    
    /**
     * requireDepartmentAllowed 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun requireDepartmentAllowed(departmentId: Long?, scope: DataScope) {
        if (departmentId == null) return
        if (scope.unrestricted) return
        if (departmentId !in scope.departmentIds) throw AccessDeniedException("无权操作其他部门的数据")
    }
    
    
    /**
     * requireUserInScope 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
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
     * requireDepartmentCodeAllowed 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun requireDepartmentCodeAllowed(departmentCode: String?, scope: DataScope) {
        if (scope.unrestricted) return
        if (departmentCode == null || departmentCode !in scope.departmentCodes) {
            throw AccessDeniedException("无权在其它部门下操作数据")
        }
    }
    
    
    /**
     * requireOwnerAllowed 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun requireOwnerAllowed(ownerId: Long?, scope: DataScope) {
        if (scope.unrestricted) return
        if (ownerId == null || ownerId !in scopeQuerySupport.ownerIdsInScope(scope)) {
            throw AccessDeniedException("无权操作其它部门的车主数据")
        }
    }
    
    
    /**
     * requireOwnerCodeAllowed 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun requireOwnerCodeAllowed(ownerCode: String?, scope: DataScope) {
        if (scope.unrestricted) return
        if (ownerCode == null || ownerCode !in scopeQuerySupport.ownerCardIdsInScope(scope)) {
            throw AccessDeniedException("无权操作其它部门的车主数据")
        }
    }
}


