package top.foxball.cartask.sms

import com.aliyun.dysmsapi20170525.models.SendSmsRequest
import com.aliyun.tea.TeaException
import com.aliyun.teaopenapi.models.Config
import com.aliyun.teautil.models.RuntimeOptions
import com.google.gson.Gson
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper

@ConfigurationProperties(prefix = "cartask.sms")
data class SmsProperties(
    val enabled: Boolean = false,
    
    val skipVerification: Boolean = false,
    val accessKeyId: String = "",
    val accessKeySecret: String = "",
    val endpoint: String = "dysmsapi.aliyuncs.com",
    val signName: String = "",
    val templateCode: String = "",
    val countryCode: String = "86",
)

@Component
class SmsClient(
    private val properties: SmsProperties, private val objectMapper: ObjectMapper, private val jsonMapper: JsonMapper
) {
    
    fun sendVerificationCode(to: String, code: String) {
        check(properties.enabled) { "短信服务未启用" }
        require(properties.signName.isNotBlank() && properties.templateCode.isNotBlank()) {
            "短信签名或模板未配置"
        }
        
        val config = Config().setAccessKeyId(properties.accessKeyId).setAccessKeySecret(properties.accessKeySecret)
            .setEndpoint("dysmsapi.aliyuncs.com").setRegionId("cn-hangzhou")
        val client = com.aliyun.dysmsapi20170525.Client(config)
        
        val sendSmsRequest = SendSmsRequest()
            .setPhoneNumbers("86$to")
            .setSignName(properties.signName)
            .setTemplateCode(properties.templateCode)
            .setTemplateParam("{\"code\":\"$code\"}")
        
        try {
            client.sendSmsWithOptions(sendSmsRequest, RuntimeOptions())
        } catch (error: TeaException) {
            println(error.message)
        }
        
    }
}
