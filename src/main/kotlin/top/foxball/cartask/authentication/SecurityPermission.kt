package top.foxball.cartask.authentication

import java.util.*

/** SecurityContext 中权限 authority 的规范形式，例如 `user:read`。 */
object SecurityPermission {
    private val codePattern = Regex("[a-z][a-z0-9_-]*:[a-z][a-z0-9_-]*")

    /**
     * normalize：转换、构建或格式化数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param value 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun normalize(value: String): String {
        val permission = value.trim().lowercase(Locale.ROOT)
        require(codePattern.matches(permission)) { "不支持的权限编码" }
        return permission
    }

    /**
     * normalizeOrNull：转换、构建或格式化数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param value 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun normalizeOrNull(value: String): String? = runCatching { normalize(value) }.getOrNull()
}
