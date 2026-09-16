package top.foxball.cartask.scope

/**
 * DepartmentLinkResolver 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */

import org.springframework.stereotype.Component
import top.foxball.cartask.entity.Department
import top.foxball.cartask.repository.DepartmentRepository


@Component
/**
 * DepartmentLinkResolver 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
class DepartmentLinkResolver(
    private val departmentRepository: DepartmentRepository,
) {
    
    
    /**
     * snapshot 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun snapshot(): DepartmentSnapshot = DepartmentSnapshot(departmentRepository.findAll())
}


/**
 * DepartmentSnapshot 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
class DepartmentSnapshot(departments: List<Department>) {
    private val departments: List<Department> = departments
    
    private val codeByCode: Map<String, String> =
        departments.associate { it.departmentNumber to it.departmentNumber }
    
    private val codeByName: Map<String, String> =
        departments.associate { it.name.trim() to it.departmentNumber }
    
    
    /**
     * toCode 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun toCode(freeText: String?): String? {
        val value = freeText?.trim()?.takeIf(String::isNotEmpty) ?: return null
        return codeByCode[value] ?: codeByName[value]
    }
    
    
    /**
     * codesOf 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun codesOf(departmentIds: Set<Long>): Set<String> {
        if (departmentIds.isEmpty()) return emptySet()
        return departments.filter { it.id in departmentIds }.map { it.departmentNumber }.toSet()
    }
    
    
    /**
     * namesOf 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun namesOf(departmentIds: Set<Long>): Set<String> {
        if (departmentIds.isEmpty()) return emptySet()
        return departments.filter { it.id in departmentIds }.map { it.name }.toSet()
    }
    
    
    /**
     * withDescendants 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun withDescendants(departmentIds: Set<Long>): Set<Long> {
        if (departmentIds.isEmpty()) return emptySet()
        val childrenByParent = departments.groupBy { it.superior?.id }
        val expanded = departmentIds.toMutableSet()
        val queue = ArrayDeque(departmentIds)
        while (queue.isNotEmpty()) {
            val parentId = queue.removeFirst()
            childrenByParent[parentId].orEmpty().forEach { child ->
                val childId = child.id
                if (childId != null && expanded.add(childId)) queue.addLast(childId)
            }
        }
        return expanded
    }
}


