package top.foxball.setup

import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import top.foxball.cartask.keytop.KeytopSignature
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.*

data class KeytopProbeResult(
    val message: String,
    /** 平台返回的车场区域个数；接口没给可数结构时为 null。 */
    val areas: Int?,
)

/**
 * 科拓开放平台凭据探测。
 *
 * 签名算法直接引用 [KeytopSignature]，不在这里另写一份：`appId`、`parkId`、`appSecret` 三者对不上
 * 时平台返回的错误是含糊的，如果探测用的签名规则和实际调用不是同一套，「探测通过但同步全失败」
 * 会变成一个很难查的问题。
 *
 * 探的是 `GetParkingPlaceArea`：只读、无副作用、不需要任何业务参数，而且它同时校验了 `parkId`
 * 是否属于这个 `appId`——只验签名的接口证明不了车场号填对了。
 */
@Component
class KeytopProbe(
    private val objectMapper: ObjectMapper,
) {
    /**
     * probe：执行数据同步、探测或文件处理。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param baseUrl 参与本次处理的输入参数。
     * @param appId 参与本次处理的输入参数。
     * @param parkId 参与本次处理的输入参数。
     * @param appSecret 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun probe(baseUrl: String, appId: String, parkId: String, appSecret: String): KeytopProbeResult {
        val base = baseUrl.trim().trimEnd('/')
        if (!base.startsWith("http://") && !base.startsWith("https://")) {
            throw SetupException("科拓接口地址必须以 http:// 或 https:// 开头")
        }
        val appIdNumber = appId.trim().toIntOrNull()
            ?: throw SetupException("appId 必须是数字（科拓平台分配的整数编号）")
        if (parkId.isBlank()) throw SetupException("车场编号 parkId 不能为空")
        if (appSecret.isBlank()) throw SetupException("appSecret 不能为空")

        val payload = linkedMapOf<String, Any?>(
            "appId" to appIdNumber,
            "parkId" to parkId.trim(),
            "serviceCode" to SERVICE_CODE,
            "ts" to System.currentTimeMillis(),
            "reqId" to UUID.randomUUID().toString(),
        )
        payload["key"] = KeytopSignature.paramsSign(payload, appSecret.trim())

        val body = try {
            objectMapper.writeValueAsString(payload)
        } catch (exception: RuntimeException) {
            throw SetupException("构造请求失败：${exception.message}", exception)
        }

        val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS)).build()
        val request = HttpRequest.newBuilder(URI.create("$base$PATH"))
            .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
            .header("Content-Type", "application/json")
            .header("version", VERSION)
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build()

        val response = try {
            client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        } catch (exception: Exception) {
            throw SetupException(describeTransport(exception), exception)
        }

        if (response.statusCode() !in 200..299) {
            throw SetupException(
                "科拓接口返回 HTTP ${response.statusCode()}，请确认接口地址是否正确（当前：$base）",
            )
        }

        val json = try {
            objectMapper.readTree(response.body())
        } catch (exception: RuntimeException) {
            throw SetupException("科拓接口返回的不是 JSON，请确认接口地址是否正确（当前：$base）", exception)
        }
        if (!json.isObject) {
            throw SetupException("科拓接口返回的不是 JSON 对象，请确认接口地址是否正确（当前：$base）")
        }

        val code = (json.get("code") ?: json.get("resCode"))?.asInt()
        val message = (json.get("message") ?: json.get("resMsg"))?.asString().orEmpty()
        if (code != SUCCESS_CODE) {
            throw SetupException(describePlatform(code, message))
        }
        return KeytopProbeResult(
            message = message.ifBlank { "调用成功" },
            areas = json.get("data")?.takeIf { it.isArray }?.size(),
        )
    }

    /** 平台用非 0 的 code 表达业务失败，具体文案不固定，只能按已知的几类给出可执行的方向。 */
    private fun describePlatform(code: Int?, message: String): String {
        val hint = when {
            message.contains("签名", ignoreCase = true) || message.contains("sign", ignoreCase = true) ->
                "appSecret 不正确"

            message.contains("appId", ignoreCase = true) -> "appId 不正确或未开通该接口权限"
            message.contains("车场", ignoreCase = true) || message.contains("park", ignoreCase = true) ->
                "parkId 不正确，或该车场不属于这个 appId"

            else -> "请核对 appId、parkId 与 appSecret"
        }
        return "科拓接口返回失败：${message.ifBlank { "code=${code ?: "未知"}" }}（$hint）"
    }

    /**
     * describeTransport：转换、构建或格式化数据。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param exception 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    private fun describeTransport(exception: Exception): String {
        val raw = generateSequence(exception as Throwable) { it.cause }
            .mapNotNull { it.message }
            .joinToString("；")
        val hint = when {
            raw.contains("UnknownHost", ignoreCase = true) -> "域名无法解析"
            raw.contains("Connection refused", ignoreCase = true) -> "目标地址拒绝连接"
            raw.contains("timed out", ignoreCase = true) || raw.contains("timeout", ignoreCase = true) ->
                "请求超时，请检查网络与出口策略"

            else -> "请检查网络与接口地址"
        }
        return "无法访问科拓接口：$hint。${raw.ifBlank { "（未返回原因）" }}"
    }

    private companion object {
        const val PATH = "/api/wec/GetParkingPlaceArea"
        const val SERVICE_CODE = "getParkingPlaceArea"
        const val VERSION = "1.0.0"
        const val SUCCESS_CODE = 0
        const val CONNECT_TIMEOUT_SECONDS = 10L
        const val REQUEST_TIMEOUT_SECONDS = 20L
    }
}
