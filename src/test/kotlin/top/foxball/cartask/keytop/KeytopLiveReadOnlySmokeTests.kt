package top.foxball.cartask.keytop

import org.springframework.web.client.RestClient
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import tools.jackson.databind.ObjectMapper
import java.time.Duration


@EnabledIfEnvironmentVariable(named = "KEYTOP_LIVE_READ_ONLY", matches = "true")
class KeytopLiveReadOnlySmokeTests {
    @org.junit.jupiter.api.Test
    fun `只读查询车辆进出记录`() {
        val service = KeytopServiceImpl(
            RestClient.builder(),
            KeytopProperties(
                baseUrl = System.getenv("KEYTOP_BASE_URL"),
                appId = System.getenv("KEYTOP_APP_ID").toInt(),
                parkId = System.getenv("KEYTOP_PARK_ID"),
                appSecret = System.getenv("KEYTOP_APP_SECRET"),
                version = System.getenv("KEYTOP_VERSION") ?: "1.0.0",
                timeout = Duration.ofSeconds(30),
            ),
            ObjectMapper(),
        )

        val response = service.getCarInoutInfo(pageIndex = 1, pageSize = 1)
        val data = response.data?.let { if (it.isTextual) ObjectMapper().readTree(it.asString()) else it }
        val firstRecord = data?.get("detailList")?.firstOrNull()
        val totalText = data?.get("totalCount")?.asText()
        val fieldNames = firstRecord?.properties()?.map { it.key }
        println(
            "Keytop 只读车辆进出查询：code=${response.code}, message=${response.message}, " +
                "total=$totalText, fields=$fieldNames",
        )
        check(response.code == 0) { "Keytop 车辆进出查询失败：${response.code} ${response.message}" }
    }
}
