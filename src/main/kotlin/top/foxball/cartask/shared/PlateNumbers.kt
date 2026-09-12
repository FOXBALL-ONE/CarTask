package top.foxball.cartask.shared

/**
 * 车牌号归一化。
 *
 * 进出记录的车牌来自科拓，车牌档案与车主数据由人工或导入维护，两边在间隔符（·）和空格上
 * 存在差异，跨表匹配前必须统一。账号生成、车主档案补建与数据范围解析三处都依赖同一套规则：
 * 只要有一处不一致，就会出现「任务认为匹配、范围解析认为不匹配」从而看不到自己记录的问题，
 * 所以只保留这一个实现。
 */
object PlateNumbers {
    private val SEPARATORS = Regex("[\\s·.。]")

    fun normalize(value: String?): String? = value
        ?.replace(SEPARATORS, "")
        ?.trim()
        ?.uppercase()
        ?.takeIf(String::isNotEmpty)

    /** 批量归一化并去重，便于直接用于范围集合。 */
    fun normalizeAll(values: Collection<String?>): Set<String> =
        values.mapNotNull(::normalize).toSet()
}
