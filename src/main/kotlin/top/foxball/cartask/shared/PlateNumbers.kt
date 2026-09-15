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

    /**
     * normalize：转换、构建或格式化数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param value 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun normalize(value: String?): String? = value
        ?.replace(SEPARATORS, "")
        ?.trim()
        ?.uppercase()
        ?.takeIf(String::isNotEmpty)

    /** 批量归一化并去重，便于直接用于范围集合。 */
    fun normalizeAll(values: Collection<String?>): Set<String> =
        values.mapNotNull(::normalize).toSet()
}
