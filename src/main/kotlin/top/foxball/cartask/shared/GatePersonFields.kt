package top.foxball.cartask.shared

/**
 * 门禁人员字段的长度与格式约束。
 *
 * 单条录入、编辑与 Excel 批量导入是三条独立的写入路径，约束必须同源。只在其中一条上校验，
 * 另一条就会把超长值直接交给 varchar 列，由数据库抛出 22001，最终以 500 的形式暴露给用户。
 *
 * 校验函数统一返回去掉首尾空白后的值，调用方直接使用返回值，避免「 13800000002 」这类
 * 带空白的值被原样存库。
 */
object GatePersonFields {
    /** 与 [top.foxball.cartask.entity.GatePerson] 的列宽一一对应。 */
    const val CODE_MAX = 64
    const val DEPT_MAX = 128
    const val NAME_MAX = 128
    const val PHONE_MAX = 32
    const val ID_CARD_MAX = 32

    /**
     * 手机号只校验「纯数字 + 长度」，不锁运营商号段。
     *
     * 锁死 `^1\d{10}$` 会连带挡掉座机、分机和境外号码，而这里真正要拦的是 `abc` 这类
     * 明显非号码的值，以及超出列宽导致写库失败的超长值。
     */
    private val PHONE_PATTERN = Regex("^\\d{6,20}$")

    /** 身份证号：18 位，末位可为 X。 */
    private val ID_CARD_PATTERN = Regex("^\\d{17}[0-9Xx]$")

    fun requireCode(value: String?, linePrefix: String = ""): String =
        requireNotBlank(value, "${linePrefix}人员编号", CODE_MAX)

    fun requireDept(value: String?, linePrefix: String = ""): String =
        requireNotBlank(value, "${linePrefix}部门", DEPT_MAX)

    fun requireName(value: String?, linePrefix: String = ""): String =
        requireNotBlank(value, "${linePrefix}姓名", NAME_MAX)

    fun requirePhone(value: String?, linePrefix: String = ""): String =
        requireNotBlank(value, "${linePrefix}手机号", PHONE_MAX)
            .also { require(it.matches(PHONE_PATTERN)) { "${linePrefix}手机号格式不正确，只能填写数字" } }

    fun requireIdCard(value: String?, linePrefix: String = ""): String =
        requireNotBlank(value, "${linePrefix}身份证号", ID_CARD_MAX)
            .also { require(it.matches(ID_CARD_PATTERN)) { "${linePrefix}身份证号必须为 18 位" } }

    private fun requireNotBlank(value: String?, label: String, maxLength: Int): String {
        val trimmed = requireNotNull(value?.trim()?.takeIf(String::isNotEmpty)) { "${label}不能为空" }
        require(trimmed.length <= maxLength) { "${label}长度不能超过 $maxLength 个字符" }
        return trimmed
    }
}
