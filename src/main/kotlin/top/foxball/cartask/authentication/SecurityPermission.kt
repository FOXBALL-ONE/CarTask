package top.foxball.cartask.authentication

import java.util.Locale

/** SecurityContext 中权限 authority 的规范形式，例如 `user:read`。 */
object SecurityPermission {
    private val codePattern = Regex("[a-z][a-z0-9_-]*:[a-z][a-z0-9_-]*")

    fun normalize(value: String): String {
        val permission = value.trim().lowercase(Locale.ROOT)
        require(codePattern.matches(permission)) { "不支持的权限编码" }
        return permission
    }

    fun normalizeOrNull(value: String): String? = runCatching { normalize(value) }.getOrNull()
}
