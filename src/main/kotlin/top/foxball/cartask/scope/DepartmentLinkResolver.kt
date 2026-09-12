package top.foxball.cartask.scope

import org.springframework.stereotype.Component
import top.foxball.cartask.entity.Department
import top.foxball.cartask.repository.DepartmentRepository

/**
 * 把业务表上的部门自由文本解析成稳定的部门编码（[Department.departmentNumber]）。
 *
 * 之所以要这一层：除 User / CarMasterInfo / AccessControl 有真正的部门外键，车主、门禁人员、
 * 人员进出记录到部门都只有一个字符串。字符串既可能是部门名（历史人工录入）也可能已经是编码，
 * 且部门名不唯一、可改名，所以必须收敛到唯一且稳定的编码上。
 *
 * 匹配规则是**先编码、后名称，都要求精确相等，绝不模糊匹配**——模糊匹配会直接把别的部门的
 * 数据匹配进来，那就是数据泄露。解析不出来返回 null，由调用方按「看不到」处理。
 *
 * **不做缓存**：部门表只有几十行，而缓存会让部门改名或新增在缓存失效前按旧名字解析，
 * 那会直接把数据划错范围。每请求取一次快照（[snapshot]）后在同一请求内复用。
 */
@Component
class DepartmentLinkResolver(
    private val departmentRepository: DepartmentRepository,
) {
    fun snapshot(): DepartmentSnapshot = DepartmentSnapshot(departmentRepository.findAll())
}

/** 一次请求内的部门解析快照；同一请求内多个实体复用同一份，避免逐行查库。 */
class DepartmentSnapshot(departments: List<Department>) {
    private val departments: List<Department> = departments

    private val codeByCode: Map<String, String> =
        departments.associate { it.departmentNumber to it.departmentNumber }

    // 同名部门只保留一个：名称本身不唯一，重名时按名称解析本就不确定，
    // 这类历史数据应由回填接口按编码修正，不在这里猜。
    private val codeByName: Map<String, String> =
        departments.associate { it.name.trim() to it.departmentNumber }

    /** 解析部门自由文本；解析不出返回 null（调用方按不可见处理）。 */
    fun toCode(freeText: String?): String? {
        val value = freeText?.trim()?.takeIf(String::isNotEmpty) ?: return null
        return codeByCode[value] ?: codeByName[value]
    }

    /** 部门 ID 集合对应的编码集合。 */
    fun codesOf(departmentIds: Set<Long>): Set<String> {
        if (departmentIds.isEmpty()) return emptySet()
        return departments.filter { it.id in departmentIds }.map { it.departmentNumber }.toSet()
    }

    /** 部门 ID 集合对应的名称集合，用于兼容只有部门名快照的历史记录。 */
    fun namesOf(departmentIds: Set<Long>): Set<String> {
        if (departmentIds.isEmpty()) return emptySet()
        return departments.filter { it.id in departmentIds }.map { it.name }.toSet()
    }

    /** 展开下级部门：把给定部门集合扩展为其自身加上所有后代部门。 */
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
