package top.itneko.keytop

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import tools.jackson.databind.ObjectMapper
import java.util.UUID
import java.security.MessageDigest
import java.util.TreeMap
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class KeytopMockControllerTests @Autowired constructor(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
) {
    @Test
    fun `valid signed requests can create and query a card`() {
        val add = request(
            "addCarCardNo",
            mapOf(
                "userId" to 1,
                "userName" to "tester",
                "cardInfo" to objectMapper.writeValueAsString(mapOf("cardName" to "测试月卡", "useName" to "张三", "tel" to "13800000000", "roomId" to "R1")),
                "carLotList" to objectMapper.writeValueAsString(listOf(mapOf("lotName" to "A区", "areaName" to "A区"))),
                "plateNoInfo" to objectMapper.writeValueAsString(listOf(mapOf("plateNo" to "闽A12345"))),
            ),
        )
        val added = mockMvc.post("/unite-api/api/wec/AddCarCardNo") {
            header("version", "1.0.0")
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(add)
        }.andReturn()
        assertEquals(200, added.response.status)
        val cardId = objectMapper.readTree(added.response.contentAsString).get("data").asString().let(objectMapper::readTree).get("cardId").asLong()

        val query = request("getCarCardInfo", mapOf("cardId" to cardId))
        val queried = mockMvc.post("/unite-api/api/wec/GetCarCardInfo") {
            header("version", "1.0.0")
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(query)
        }.andReturn()
        assertEquals(200, queried.response.status)
        assertEquals(cardId, objectMapper.readTree(queried.response.contentAsString).get("data").asString().let(objectMapper::readTree).get("cardId").asLong())
    }

    @Test
    fun `invalid signatures are rejected`() {
        val request = request("getParkingPlaceArea", emptyMap()).toMutableMap().apply { this["key"] = "00000000000000000000000000000000" }
        val result = mockMvc.post("/unite-api/api/wec/GetParkingPlaceArea") {
            header("version", "1.0.0")
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andReturn()
        assertEquals(400, result.response.status)
    }

    private fun request(serviceCode: String, business: Map<String, Any?>): Map<String, Any?> {
        val result = linkedMapOf<String, Any?>("appId" to 12250, "parkId" to "591007282", "serviceCode" to serviceCode, "ts" to System.currentTimeMillis(), "reqId" to UUID.randomUUID().toString())
        result.putAll(business)
        result["key"] = sign(result)
        return result
    }

    private fun sign(request: Map<String, Any?>): String {
        val fields = TreeMap<String, String>()
        request.forEach { (name, value) ->
            if (name == "key" || name == "appId" || value == null || value is Map<*, *> || value is Iterable<*> || value.toString().isEmpty()) return@forEach
            fields[name] = value.toString()
        }
        val plain = fields.entries.joinToString("&") { "${it.key}=${it.value}" } + "&secret"
        return MessageDigest.getInstance("MD5").digest(plain.toByteArray()).joinToString("") { "%02X".format(it.toInt() and 0xff) }
    }
}
