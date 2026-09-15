package top.foxball.setup

import com.aliyun.dysmsapi20180501.Client
import com.aliyun.dysmsapi20180501.models.SendMessageWithTemplateRequest
import com.aliyun.teaopenapi.models.Config
import com.aliyun.teautil.models.RuntimeOptions
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

data class SmsProbeResult(
    val phone: String,
    val code: String,
)

/**
 * 短信凭据探测：真发一条测试短信。
 *
 * 阿里云的短信凭据没法「只校验不发」——AK 对不对、签名过没过审、模板可不可用，都要到真正投递
 * 那一刻才有结论。而这几项的任何一项配错，都会让短信登录在整个系统上线后完全不可用，且只在
 * 用户点「获取验证码」时才暴露。所以这里花一条短信的钱，把它提前到配置阶段。
 *
 * 发送内容与登录验证码走同一套模板：模板参数的键（`code`）必须和模板本身一致，用别的键探测
 * 通过了也没意义。
 */
@Component
class SmsProbe(
    private val objectMapper: ObjectMapper,
) {
    /**
     * probe：执行数据同步、探测或文件处理。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param accessKeyId 参与本次处理的输入参数。
     * @param accessKeySecret 参与本次处理的输入参数。
     * @param endpoint 参与本次处理的输入参数。
     * @param signName 参与本次处理的输入参数。
     * @param templateCode 参与本次处理的输入参数。
     * @param phone 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
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
     * describe：转换、构建或格式化数据。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param exception 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
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

        /** 与登录验证码同长度同形态，收到这条短信就能确认整条链路可用。 */
        const val TEST_CODE = "123456"

        /** 与 [top.foxball.cartask.authentication.SmsVerificationService] 的入参校验保持一致。 */
        val PHONE_PATTERN = Regex("^\\+?[0-9]{6,20}$")
    }
}
