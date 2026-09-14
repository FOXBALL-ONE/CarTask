package top.foxball.cartask.shared

/**
 * 系统自动开通账号时使用的初始凭据。
 *
 * 集中在这里而不是各自写一份：业主账号生成任务与「登记进出申请时顺带建档」都会下发这个密码，
 * 两处各写一份字面量，改一处就漏一处，而漏的那一处会继续发一个已经作废的密码。
 */
object InitialCredentials {
    /** 自动开通账号的初始密码；属于公开信息，因此这类账号一律强制首次登录改密。 */
    const val PASSWORD = "Fqjg20221022"

    /** 自动开通账号的邮箱后缀；平台要求邮箱非空，但这类账号没有真实邮箱。 */
    const val EMAIL_DOMAIN = "auto.local"

    /** 按登录名拼出占位邮箱。 */
    fun placeholderEmail(username: String): String = "$username@$EMAIL_DOMAIN"
}
