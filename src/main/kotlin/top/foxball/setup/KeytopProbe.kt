package top.foxball.setup

/**
 * KeytopProbe 配置引导组件说明。
 *
 * 该文件负责系统初始化向导中的配置探测、持久化、校验或 Web 入口逻辑。
 */
/**
 * KeytopProbe 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */

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

/**
 * KeytopProbeResult 的职责与行为说明。
 * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
 */
data class KeytopProbeResult(
    val message: String,
    
    val areas: Int?,
)


@Component
/**
 * KeytopProbe 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
/**
 * KeytopProbe 的职责与行为说明。
 * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
 */
class KeytopProbe(
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
    
    
    /**
     * describePlatform 的职责与行为说明。
     * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
     */
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
     * describeTransport 的职责与行为说明。
     * 该声明负责配置引导流程中的相关数据处理、校验或服务调用。
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





