package top.foxball.cartask.authentication

import java.util.Locale

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

    fun normalize(value: String): String {
        val role = value.trim().uppercase(Locale.ROOT).removePrefix("ROLE_")
        require(role in allowedRoles) { "不支持的用户角色" }
        return role
    }

    fun normalizeOrNull(value: String): String? = runCatching { normalize(value) }.getOrNull()

    fun authority(value: String): String = "ROLE_${normalize(value)}"

    fun priorityOf(role: String): Int = priorities[normalize(role)] ?: 0
}
