package top.foxball.cartask.authentication

import java.util.Locale

/** 认证与授权层允许使用的角色编码，统一去除 ROLE_ 前缀并转为大写。 */
object SecurityRole {
    private val allowedRoles = setOf("SUPER_ADMIN", "ADMIN", "USER")
    val SUPER_ADMIN_GOVERNANCE_PERMISSIONS = setOf(
        "role:manage",
        "permission:manage",
        "user:role-assign",
        "user:disable",
    )
    
    fun normalize(value: String): String {
        val role = value.trim().uppercase(Locale.ROOT).removePrefix("ROLE_")
        require(role in allowedRoles) { "不支持的用户角色" }
        return role
    }
    
    fun normalizeOrNull(value: String): String? = runCatching { normalize(value) }.getOrNull()
    
    fun authority(value: String): String = "ROLE_${normalize(value)}"
}
