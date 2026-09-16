package top.foxball.setup

/**
 * SmsProbe 配置引导组件说明。
 *
 * 该文件负责系统初始化向导中的配置探测、持久化、校验或 Web 入口逻辑。
 */
/**
 * SmsProbe 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */

import com.aliyun.dysmsapi20180501.Client
import com.aliyun.dysmsapi20180501.models.SendMessageWithTemplateRequest
import com.aliyun.teaopenapi.models.Config
import com.aliyun.teautil.models.RuntimeOptions
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * SmsProbeResult 的职责与行为说明。
 * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
 */
data class SmsProbeResult(
    val phone: String,
    val code: String,
)


@Component
/**
 * SmsProbe 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
/**
 * SmsProbe 的职责与行为说明。
 * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
 */
class SmsProbe(
    private val objectMapper: ObjectMapper,
) {
    
    
    /**
     * probe 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    /**
     * probe 的职责与行为说明。
     * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
     */
    fun probe(
        accessKeyId: String,
        accessKeySecret: String,
        endpoint: String,
        signName: String,
        templateCode: String,
        phone: String,
    ): SmsProbeResult {
        if (accessKeyId.isBlank()) throw SetupException("AccessKey ID 不能为空")
        if (accessKeySecret.isBlank()) throw SetupException("AccessKey Secret 不能为空")
        if (signName.isBlank()) throw SetupException("短信签名不能为空")
        if (templateCode.isBlank()) throw SetupException("短信模板编号不能为空")
        val normalized = phone.trim()
        if (!PHONE_PATTERN.matches(normalized)) {
            throw SetupException("接收测试短信的手机号格式无效")
        }
        
        val credential = com.aliyun.credentials.models.Config()
            .setAccessKeyId(accessKeyId.trim())
            .setAccessKeySecret(accessKeySecret.trim())
        val config = Config().setCredential(com.aliyun.credentials.Client(credential))
        config.endpoint = endpoint.trim().ifEmpty { DEFAULT_ENDPOINT }
        
        val request = SendMessageWithTemplateRequest()
            .setTo(normalized)
            .setFrom(signName.trim())
            .setTemplateCode(templateCode.trim())
            .setTemplateParam(objectMapper.writeValueAsString(mapOf("code" to TEST_CODE)))
        
        val body = try {
            Client(config).sendMessageWithTemplateWithOptions(request, RuntimeOptions()).body
        } catch (exception: Exception) {
            throw SetupException(describe(exception), exception)
        }
        
        if (body?.responseCode != SUCCESS_CODE) {
            throw SetupException(
                "短信发送失败：${body?.responseDescription ?: "平台未返回原因"}" +
                        "（代码 ${body?.responseCode ?: "未知"}），请核对 AccessKey、签名与模板的可用性",
            )
        }
        return SmsProbeResult(phone = normalized, code = TEST_CODE)
    }
    
    
    /**
     * describe 的职责与行为说明。
     * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
     */
    private fun describe(exception: Exception): String {
        val raw = generateSequence(exception as Throwable) { it.cause }
            .mapNotNull { it.message }
            .joinToString("；")
        val hint = when {
            raw.contains("InvalidAccessKeyId", ignoreCase = true) ||
                    raw.contains("SignatureDoesNotMatch", ignoreCase = true) -> "AccessKey ID 或 Secret 不正确"
            
            raw.contains("UnknownHost", ignoreCase = true) -> "短信接口域名无法解析，请检查网络与 DNS"
            raw.contains("timeout", ignoreCase = true) -> "请求超时，请检查服务器出口网络"
            else -> "请检查 AccessKey 与网络可达性"
        }
        return "调用阿里云短信接口失败：$hint。${raw.ifBlank { "（未返回原因）" }}"
    }
    
    private companion object {
        const val DEFAULT_ENDPOINT = "dysmsapi.aliyuncs.com"
        const val SUCCESS_CODE = "OK"
        
        
        const val TEST_CODE = "123456"
        
        
        val PHONE_PATTERN = Regex("^\\+?[0-9]{6,20}$")
    }
}





