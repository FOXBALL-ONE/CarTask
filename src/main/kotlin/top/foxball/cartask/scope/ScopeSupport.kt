package top.foxball.cartask.scope

/**
 * 只有部门归属的实体统一实现本接口，让「这个实体是否已接入数据范围」变成编译期可查的事实，
 * 而不是靠人工记得在每个新列表接口里加过滤。
 */
interface DepartmentScoped {
    /** 历史自由文本部门字段（部门名或编码）。 */
    val scopeDeptFreeText: String?

    /** 稳定部门编码；新写入的数据应当直接落这一列。 */
    val scopeDeptCode: String?
}

/**
 * 内存过滤：把部门维度的范围应用到已加载的列表。
 *
 * **解析不出部门归属的行一律不可见**（fail closed）。反过来放开就是静默的全量泄露。
 *
 * 本人范围（[ScopeKind.SELF]）在这里返回空列表：本函数服务的是车主、车牌、门禁人员这类
 * 主数据管理列表，普通用户本来就没有这些接口的权限。普通用户看自己的进出记录走记录表各自的
 * 范围谓词，不是这里。
 */
fun <T : DepartmentScoped> List<T>.scoped(scope: DataScope, departments: DepartmentSnapshot): List<T> =
    when (scope.kind) {
        ScopeKind.ALL -> this
        ScopeKind.DEPARTMENTS -> filter { row ->
            val code = row.scopeDeptCode ?: departments.toCode(row.scopeDeptFreeText)
            code != null && code in scope.departmentCodes
        }
        ScopeKind.SELF -> emptyList()
    }
