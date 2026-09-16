package top.foxball.cartask.keytop

/**
 * KeytopSignature 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.*


/**
 * KeytopSignature 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
object KeytopSignature {
    
    
    /**
     * paramsSign 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
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


/**
 * SignUtils 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
object SignUtils {
    
    
    /**
     * paramsSign 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun paramsSign(params: Map<String, Any?>, appSecret: String): String =
        KeytopSignature.paramsSign(params, appSecret)
}


