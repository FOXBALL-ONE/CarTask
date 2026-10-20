package top.foxball.cartask.authentication

object PasswordPolicy {
    const val MIN_LENGTH = 8
    const val MIN_CHARACTER_CLASSES = 3

    fun errorMessage(password: String, label: String = "密码"): String? {
        if (password.length < MIN_LENGTH) return "${label}至少需要 $MIN_LENGTH 位字符"
        if (password.any(Char::isWhitespace)) return "${label}不能包含空白字符"

        val characterClasses = listOf(
            password.any(Char::isLowerCase),
            password.any(Char::isUpperCase),
            password.any(Char::isDigit),
            password.any { !it.isLetterOrDigit() },
        ).count { it }
        if (characterClasses < MIN_CHARACTER_CLASSES) {
            return "${label}需包含大写字母、小写字母、数字、特殊符号中的至少 $MIN_CHARACTER_CLASSES 类"
        }
        return null
    }
}
