package top.foxball.cartask.keytop

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.*

/** 科拓开放平台 paramsSign 签名算法。 */
object KeytopSignature {
    /**
     * 按平台规则过滤并排序请求参数，然后追加 appSecret 计算大写 MD5。
     * 数组、Map 和 Iterable 不参与签名，但仍然可以原样出现在请求体中。
     */
    fun paramsSign(params: Map<String, Any?>, appSecret: String): String {
        val filtered = TreeMap<String, String>()
        params.forEach { (name, value) ->
            if (name == "key" || name == "appId" || value == null) return@forEach
            if (value is String && value.isEmpty()) return@forEach
            if (value is Map<*, *> || value is Iterable<*> || value.javaClass.isArray) return@forEach
            filtered[name] = when (value) {
                is Boolean -> value.toString()
                else -> value.toString()
            }
        }

        val plainText = filtered.entries.joinToString("&") { (name, value) -> "$name=$value" } + "&" + appSecret
        val digest = MessageDigest.getInstance("MD5").digest(plainText.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { byte -> "%02X".format(byte.toInt() and 0xff) }
    }
}

/** 与平台 PHP SDK 命名保持一致的签名工具别名。 */
object SignUtils {
    /**
     * paramsSign：执行当前模块中的业务操作。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param params 参与本次处理的输入参数。
     * @param appSecret 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun paramsSign(params: Map<String, Any?>, appSecret: String): String =
        KeytopSignature.paramsSign(params, appSecret)
}
