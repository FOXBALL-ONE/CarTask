package top.foxball.cartask.sms

import com.aliyun.dysmsapi20180501.Client
import com.aliyun.dysmsapi20180501.models.SendMessageWithTemplateRequest
import com.aliyun.teaopenapi.models.Config
import com.aliyun.teautil.models.RuntimeOptions
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

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
    /**
     * createClient：创建、保存或初始化相关数据。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
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

    /**
     * sendVerificationCode：执行当前模块中的业务操作。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param to 参与本次处理的输入参数。
     * @param code 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
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
