package top.foxball.cartask.scope

/**
 * DataScope 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */


/**
 * ScopeKind 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
enum class ScopeKind {
    
    ALL,
    
    
    DEPARTMENTS,
    
    
    SELF,
}


data class DataScope(
    val kind: ScopeKind,
    
    val departmentIds: Set<Long> = emptySet(),
    
    val departmentCodes: Set<String> = emptySet(),
    
    val departmentNames: Set<String> = emptySet(),
    
    val userId: Long? = null,
    
    val phone: String? = null,
    
    val carNumbers: Set<String> = emptySet(),
    
    val gatePersonCodes: Set<String> = emptySet(),
    
    val gatePersonNames: Set<String> = emptySet(),
) {
    
    val unrestricted: Boolean get() = kind == ScopeKind.ALL
    
    
    val deniesEverything: Boolean
        get() = when (kind) {
            ScopeKind.ALL -> false
            ScopeKind.DEPARTMENTS -> departmentIds.isEmpty()
            ScopeKind.SELF -> carNumbers.isEmpty() && gatePersonCodes.isEmpty() && gatePersonNames.isEmpty()
        }
    
    companion object {
        
        val All = DataScope(ScopeKind.ALL)
        
        
        /**
         * departments 函数：执行与该组件职责相关的业务操作。
         * 参数和返回值遵循调用方与领域服务之间的约定。
         */
        fun departments(ids: Set<Long>, codes: Set<String>, names: Set<String>): DataScope =
            DataScope(ScopeKind.DEPARTMENTS, departmentIds = ids, departmentCodes = codes, departmentNames = names)
        
        
        /**
         * self 函数：执行与该组件职责相关的业务操作。
         * 参数和返回值遵循调用方与领域服务之间的约定。
         */
        fun self(
            userId: Long,
            phone: String?,
            carNumbers: Set<String>,
            gatePersonCodes: Set<String>,
            gatePersonNames: Set<String>,
        ): DataScope = DataScope(
            ScopeKind.SELF,
            userId = userId,
            phone = phone,
            carNumbers = carNumbers,
            gatePersonCodes = gatePersonCodes,
            gatePersonNames = gatePersonNames,
        )
    }
}


