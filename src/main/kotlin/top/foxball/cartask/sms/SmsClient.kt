package top.foxball.cartask.sms

import com.aliyun.dysmsapi20180501.Client
import com.aliyun.dysmsapi20180501.models.SendMessageWithTemplateRequest
import com.aliyun.teaopenapi.models.Config
import com.aliyun.teautil.models.RuntimeOptions
import tools.jackson.databind.ObjectMapper
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@ConfigurationProperties(prefix = "cartask.sms")
data class SmsProperties(
    val enabled: Boolean = false,
    /**
     * 临时跳过短信验证的总开关：为 true 时**既不真发短信，也不校验验证码**。
     *
     * 只在短信通道不可用期间用来跑通流程（登录、重置密码、换绑手机号都会失去这一步校验），
     * 默认 false，线上必须保持 false。
     */
    val skipVerification: Boolean = false,
    val accessKeyId: String = "",
    val accessKeySecret: String = "",
    val endpoint: String = "dysmsapi.aliyuncs.com",
    val signName: String = "",
    val templateCode: String = "",
    val countryCode: String = "86",
)

@Component
class SmsClient(private val properties: SmsProperties, private val objectMapper: ObjectMapper) {
    private fun createClient(): Client {
        
        // 工程代码建议使用更安全的无 AK 方式，凭据配置方式请参见：https://help.aliyun.com/document_detail/378657.html。
        require(properties.accessKeyId.isNotBlank() && properties.accessKeySecret.isNotBlank()) {
            "短信服务凭据未配置"
        }
        val credential = com.aliyun.credentials.models.Config()
            .setAccessKeyId(properties.accessKeyId)
            .setAccessKeySecret(properties.accessKeySecret)
        val credentialClient = com.aliyun.credentials.Client(credential)
        val config = Config().setCredential(credentialClient)
        
        // Endpoint 请参考 https://api.aliyun.com/product/Dysmsapi
        config.endpoint = properties.endpoint
        return Client(config)
    }
    
    fun sendVerificationCode(to: String, code: String) {
        check(properties.enabled) { "短信服务未启用" }
        require(properties.signName.isNotBlank() && properties.templateCode.isNotBlank()) {
            "短信签名或模板未配置"
        }
       val client = createClient()
        val request = SendMessageWithTemplateRequest()
            .setTo(to)
            .setFrom(properties.signName)
            .setTemplateCode(properties.templateCode)
            .setTemplateParam(objectMapper.writeValueAsString(mapOf("code" to code)))
        client.sendMessageWithTemplateWithOptions(request, RuntimeOptions())
    }
}
