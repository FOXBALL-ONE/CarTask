package top.foxball.cartask.shared

/**
 * 列表页「编号」列的取值规则。
 *
 * 各列表页展示的编号都是 ID 补零到 4 位（ID 1 显示为 0001），所以按编号搜索时「1」和
 * 「0001」都要能命中同一条记录——否则用户照着屏幕上看到的编号原样输入，反而搜不到。
 */
object SerialNumbers {
    private const val WIDTH = 4

    /** 编号的展示形式：ID 不足 4 位时左侧补 0。 */
    fun format(id: Long): String = id.toString().padStart(WIDTH, '0')

    /**
     * 编号是否命中关键词：展示形式与原始 ID 任一包含关键词即算命中。
     *
     * 搜索框只有一个关键词、不区分搜的是哪个字段，所以这里按「包含」而不是「相等」匹配，
     * 与车牌号、车主名的匹配口径保持一致。
     */
    fun matches(id: Long?, keyword: String): Boolean =
        id != null && (id.toString().contains(keyword) || format(id).contains(keyword))
}
