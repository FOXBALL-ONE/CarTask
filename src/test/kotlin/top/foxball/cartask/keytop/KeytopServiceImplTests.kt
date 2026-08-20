package top.foxball.cartask.keytop

import com.sun.net.httpserver.HttpServer
import org.springframework.web.client.RestClient
import tools.jackson.databind.ObjectMapper
import java.net.InetSocketAddress
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertFailsWith

class KeytopServiceImplTests {
    private fun service(baseUrl: String): KeytopServiceImpl = KeytopServiceImpl(
        RestClient.builder(),
        KeytopProperties(
            baseUrl = baseUrl,
            appId = 12250,
            parkId = "591007282",
            appSecret = "secret",
        ),
        ObjectMapper(),
    )

    @Test
    fun `rejects invalid pagination and identifiers before making a request`() {
        val service = service("http://127.0.0.1:1/unite-api")

        assertFailsWith<IllegalArgumentException> { service.getCarCardList(pageIndex = 0, pageSize = 10) }
        assertFailsWith<IllegalArgumentException> { service.queryCarBlackInfo(pageIndex = 1, pageSize = 0) }
        assertFailsWith<IllegalArgumentException> { service.getCarCardInfo(cardId = 0) }
        assertFailsWith<IllegalArgumentException> { service.getCarCardInfo(plateNo = " ") }
    }

    @Test
    fun `rejects reversed validity and inout ranges`() {
        val service = service("http://127.0.0.1:1/unite-api")
        val start = LocalDateTime.of(2026, 1, 2, 0, 0)
        val end = start.minusDays(1)

        assertFailsWith<IllegalArgumentException> {
            service.payCarCardFee(KeytopPayCarCardFeeRequest(1, "operator", 2, 1, start, end))
        }
        assertFailsWith<IllegalArgumentException> {
            service.getCarInoutInfo(startTime = start, endTime = end)
        }
    }

    @Test
    fun `posts signed request to the configured platform path`() {
        val requestBody = AtomicReference<String>()
        val requestVersion = AtomicReference<String>()
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/unite-api/api/wec/GetCarCardList") { exchange ->
            requestBody.set(exchange.requestBody.bufferedReader().use { it.readText() })
            requestVersion.set(exchange.requestHeaders.getFirst("version"))
            val response = """{"code":0,"message":"success","data":{"total":1}}""".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()

        try {
            val objectMapper = ObjectMapper()
            val properties = KeytopProperties(
                baseUrl = "http://127.0.0.1:${server.address.port}/unite-api",
                appId = 12250,
                parkId = "591007282",
                appSecret = "secret",
            )
            val service = KeytopServiceImpl(RestClient.builder(), properties, objectMapper)

            val response = service.getCarCardList(pageIndex = 2, pageSize = 50)

            assertEquals(0, response.code)
            assertEquals("success", response.message)
            assertEquals(1, response.data?.get("total")?.asInt())
            assertEquals("1.0.0", requestVersion.get())
            val json = objectMapper.readTree(assertNotNull(requestBody.get()))
            assertEquals(12250, json.get("appId").asInt())
            assertEquals("591007282", json.get("parkId").asString())
            assertEquals("getCarCardList", json.get("serviceCode").asString())
            assertEquals(2, json.get("pageIndex").asInt())
            assertEquals(50, json.get("pageSize").asInt())
            assertEquals(
                KeytopSignature.paramsSign(
                    mapOf(
                        "appId" to json.get("appId").asInt(),
                        "parkId" to json.get("parkId").asString(),
                        "serviceCode" to json.get("serviceCode").asString(),
                        "ts" to json.get("ts").asLong(),
                        "reqId" to json.get("reqId").asString(),
                        "pageIndex" to json.get("pageIndex").asInt(),
                        "pageSize" to json.get("pageSize").asInt(),
                    ),
                    properties.appSecret,
                ),
                json.get("key").asString(),
            )
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `reports a non-object platform response clearly`() {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/unite-api/api/wec/GetParkingPlaceArea") { exchange ->
            val response = "[]".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()

        try {
            val exception = assertFailsWith<IllegalArgumentException> {
                service("http://127.0.0.1:${server.address.port}/unite-api").getParkingPlaceArea()
            }

            assertEquals("Keytop returned a non-object response", exception.message)
        } finally {
            server.stop(0)
        }
    }
}
