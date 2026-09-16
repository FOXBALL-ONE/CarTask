package top.foxball.cartask.scope

/**
 * ScopeSupport 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */


/**
 * DepartmentScoped 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
interface DepartmentScoped {
    
    val scopeDeptFreeText: String?
    
    
    val scopeDeptCode: String?
}


fun <T : DepartmentScoped> List<T>.scoped(scope: DataScope, departments: DepartmentSnapshot): List<T> =
    when (scope.kind) {
        ScopeKind.ALL -> this
        ScopeKind.DEPARTMENTS -> filter { row ->
            val code = row.scopeDeptCode ?: departments.toCode(row.scopeDeptFreeText)
            code != null && code in scope.departmentCodes
        }
        
        ScopeKind.SELF -> emptyList()
    }


