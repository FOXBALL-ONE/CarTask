package top.foxball.cartask.scope

/** 数据范围的三种形态。 */
enum class ScopeKind {
    /** 不限部门，可见全部数据。 */
    ALL,

    /** 限定在若干部门内。 */
    DEPARTMENTS,

    /** 仅本人：本人的车牌与本人的门禁身份。 */
    SELF,
}

/**
 * 一次请求生效的数据范围。
 *
 * 之所以是值对象而不是裸枚举：范围的判定结果（部门集合、本人车牌集合）必须跟着走，
 * 否则每个调用点都要自己再解析一遍，既重复又容易漏。
 *
 * 约定的**失败语义是「看不到」**：解析不出部门归属的行、解析不出归属人的行，都不落在
 * 任何范围里，因此对受限角色不可见。反过来（解析不出就放开）会变成静默的全量泄露。
 */
data class DataScope(
    val kind: ScopeKind,
    /** DEPARTMENTS：已展开下级部门后的部门 ID 集合。 */
    val departmentIds: Set<Long> = emptySet(),
    /** DEPARTMENTS：部门编码集合。业务表存的是编码而不是名称（只有编码唯一）。 */
    val departmentCodes: Set<String> = emptySet(),
    /** DEPARTMENTS：部门名称集合，用于兼容只有部门名快照的历史数据。 */
    val departmentNames: Set<String> = emptySet(),
    /** SELF：当前用户 ID。 */
    val userId: Long? = null,
    /** SELF：当前用户手机号，用于按手机号关联车主。 */
    val phone: String? = null,
    /** SELF：本人名下的车牌号（已归一化）。 */
    val carNumbers: Set<String> = emptySet(),
    /** SELF：本人对应的门禁人员编码。 */
    val gatePersonCodes: Set<String> = emptySet(),
    /** SELF：本人对应的门禁人员姓名。 */
    val gatePersonNames: Set<String> = emptySet(),
) {
    /** 是否不加任何限制。 */
    val unrestricted: Boolean get() = kind == ScopeKind.ALL

    /**
     * 该范围是否不可能匹配到任何数据。
     *
     * 部门管理没有被分配任何部门、普通用户没有关联到任何车牌时都会是 true。
     * 这是「默认拒绝」的正常结果，不是错误，调用方按空结果返回即可。
     */
    val deniesEverything: Boolean
        get() = when (kind) {
            ScopeKind.ALL -> false
            ScopeKind.DEPARTMENTS -> departmentIds.isEmpty()
            ScopeKind.SELF -> carNumbers.isEmpty() && gatePersonCodes.isEmpty() && gatePersonNames.isEmpty()
        }

    companion object {
        /** 不受限范围，仅用于后台任务与超级管理员显式的全局查询。 */
        val All = DataScope(ScopeKind.ALL)

        fun departments(ids: Set<Long>, codes: Set<String>, names: Set<String>): DataScope =
            DataScope(ScopeKind.DEPARTMENTS, departmentIds = ids, departmentCodes = codes, departmentNames = names)

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
