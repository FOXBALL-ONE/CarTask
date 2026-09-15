package top.foxball.cartask.authentication

import java.util.*

/** 认证与授权层允许使用的角色编码，统一去除 ROLE_ 前缀并转为大写。 */
object SecurityRole {
    /** 超级管理员：全局治理与全部业务能力。 */
    const val SUPER_ADMIN = "SUPER_ADMIN"

    /** 平台管理：平台范围内的运营能力，数据范围默认为全部，可切换工作部门收窄。 */
    const val ADMIN = "ADMIN"

    /** 部门管理：只在被分配的部门范围内运营，数据范围始终受限。 */
    const val DEPT_ADMIN = "DEPT_ADMIN"

    /** 普通用户：只看本人车辆与本人的进出记录。 */
    const val USER = "USER"

    private val allowedRoles = setOf(SUPER_ADMIN, ADMIN, DEPT_ADMIN, USER)

    /** 可进入后台运营接口的角色；@PreAuthorize 中判断管理身份时统一引用这里。 */
    val ADMIN_ROLES = setOf(SUPER_ADMIN, ADMIN, DEPT_ADMIN)

    val SUPER_ADMIN_GOVERNANCE_PERMISSIONS = setOf(
        "role:manage",
        "permission:manage",
        "user:role-assign",
        "user:disable",
    )

    /** 角色优先级，数值越大权限越高；用于 roleIds 多角色并存时确定性推导主角色。 */
    private val priorities = mapOf(SUPER_ADMIN to 3, ADMIN to 2, DEPT_ADMIN to 1, USER to 0)

    /**
     * normalize：转换、构建或格式化数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param value 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun normalize(value: String): String {
        val role = value.trim().uppercase(Locale.ROOT).removePrefix("ROLE_")
        require(role in allowedRoles) { "不支持的用户角色" }
        return role
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

    /**
     * authority：完成身份认证、令牌或验证码处理。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param value 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun authority(value: String): String = "ROLE_${normalize(value)}"

    /**
     * priorityOf：执行当前模块中的业务操作。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param role 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun priorityOf(role: String): Int = priorities[normalize(role)] ?: 0
}
