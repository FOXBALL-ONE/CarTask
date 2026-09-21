package top.itneko.keytop

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.*

@RestController
@RequestMapping("/unite-api")
class KeytopMockController(
    private val properties: KeytopMockProperties,
    private val objectMapper: ObjectMapper,
) {
    private val store = KeytopMockStore(objectMapper)
    
    @PostMapping("/api/wec/GetCarCardList")
    fun getCarCardList(@RequestHeader version: String?, @RequestBody request: JsonNode) =
        handle(version, request, "getCarCardList") {
            val pageIndex = request.requiredInt("pageIndex")
            val pageSize = request.requiredInt("pageSize")
            require(pageIndex > 0 && pageSize > 0) { "pageIndex和pageSize必须大于0" }
            val all = store.listCards()
            success(
                mapOf(
                    "pageIndex" to pageIndex,
                    "carCardList" to page(all, pageIndex, pageSize),
                    "pageSize" to pageSize,
                    "totalCount" to all.size
                )
            )
        }
    
    @PostMapping("/api/wec/AddCarCardNo")
    fun addCarCard(@RequestHeader version: String?, @RequestBody request: JsonNode) =
        handle(version, request, "addCarCardNo") {
            val card = store.addCard(
                request.requiredJson("cardInfo"),
                request.requiredJson("carLotList"),
                request.requiredJson("plateNoInfo")
            )
            success(card)
        }
    
    @PostMapping("/api/wec/GetCarCardInfo")
    fun getCarCardInfo(@RequestHeader version: String?, @RequestBody request: JsonNode) =
        handle(version, request, "getCarCardInfo") {
            val card =
                request.longValue("cardId")?.let(store::card) ?: store.cardByPlate(request.requiredText("plateNo"))
            success(card)
        }
    
    @PostMapping("/api/wec/ModifyCarCardNo")
    fun modifyCarCard(@RequestHeader version: String?, @RequestBody request: JsonNode) =
        handle(version, request, "modifyCarCardNo") {
            val card = store.updateCard(
                request.requiredJson("cardInfo"),
                request.requiredJson("carLotList"),
                request.requiredJson("plateNoInfo")
            )
            success(card)
        }
    
    @PostMapping("/api/wec/DelCarCardInfo")
    fun deleteCarCard(@RequestHeader version: String?, @RequestBody request: JsonNode) =
        handle(version, request, "delCarCardInfo") {
            store.deleteCard(request.requiredLong("cardId"))
            success(emptyMap<String, Any>())
        }
    
    @PostMapping("/api/wec/PayCarCardFee")
    fun payCarCard(@RequestHeader version: String?, @RequestBody request: JsonNode) =
        handle(version, request, "payCarCardFee") {
            val cardId = request.requiredLong("cardId")
            store.updateValidity(cardId, request.requiredText("validFrom"), request.requiredText("validTo"))
            success(mapOf("cardId" to cardId, "orderNo" to request.requiredText("orderNo")))
        }
    
    @PostMapping("/api/wec/RefundCarCardFee")
    fun refundCarCard(@RequestHeader version: String?, @RequestBody request: JsonNode) =
        handle(version, request, "refundCarCardFee") {
            val cardId = request.requiredLong("cardId")
            store.updateValidity(cardId, request.requiredText("validFrom"), request.requiredText("validTo"))
            success(
                mapOf(
                    "cardId" to cardId,
                    "orderNo" to request.requiredText("orderNo"),
                    "refundNumber" to request.requiredInt("refundNumber")
                )
            )
        }
    
    @PostMapping("/api/carCard/GetCardInfoByUser")
    fun getCardInfoByUser(@RequestHeader version: String?, @RequestBody request: JsonNode) =
        handle(version, request, "getCardInfoByUser") {
            success(store.userCard(request.requiredText("plateNo")))
        }
    
    @PostMapping("/api/wec/GetCarInoutInfo")
    fun getCarInoutInfo(@RequestHeader version: String?, @RequestBody request: JsonNode) =
        handle(version, request, "getCarInoutInfo") {
            val pageIndex = request.requiredInt("pageIndex")
            val pageSize = request.requiredInt("pageSize")
            require(pageIndex > 0 && pageSize > 0) { "pageIndex和pageSize必须大于0" }
            val records = store.inoutRecords(request.textValue("plateNo"))
            success(
                mapOf(
                    "pageIndex" to pageIndex,
                    "detailList" to page(records, pageIndex, pageSize),
                    "pageSize" to pageSize,
                    "totalCount" to records.size
                )
            )
        }
    
    @PostMapping("/api/wec/GetParkingPlaceArea")
    fun getParkingPlaceArea(@RequestHeader version: String?, @RequestBody request: JsonNode) =
        handle(version, request, "getParkingPlaceArea") {
            success(
                mapOf(
                    "areaInfo" to listOf(mapOf("areaCode" to 1, "areaName" to "模拟区域", "placeCount" to 100)),
                    "parkArea" to "1",
                    "totalPlaceCount" to 100
                )
            )
        }
    
    @PostMapping("/api/wec/GetDictList")
    fun getDictList(@RequestHeader version: String?, @RequestBody request: JsonNode) =
        handle(version, request, "GetDictDataList") {
            success(
                listOf(
                    mapOf(
                        "id" to 1,
                        "dictType" to (request.textValue("dictType") ?: "CAR_TYPE"),
                        "dictName" to "车牌类型",
                        "dictKey" to "临时车",
                        "dictValue" to "0",
                        "dictSort" to 0,
                        "isDefault" to 0,
                        "dictDesc" to "模拟字典",
                        "dictStatus" to 1,
                        "createTime" to Instant.now().toEpochMilli(),
                        "updateTime" to Instant.now().toEpochMilli(),
                        "lotCode" to properties.parkId,
                        "changeable" to false,
                        "extendMark" to 0
                    )
                )
            )
        }
    
    @PostMapping("/api/blacklist/QueryCarBlackInfo")
    fun queryBlacklist(@RequestHeader version: String?, @RequestBody request: JsonNode) =
        handle(version, request, "queryCarBlackInfo") {
            val pageIndex = request.requiredInt("pageIndex")
            val pageSize = request.requiredInt("pageSize")
            require(pageIndex > 0 && pageSize > 0) { "pageIndex和pageSize必须大于0" }
            val all = store.blacklistList(request.textValue("plateNo"))
            success(
                mapOf(
                    "pageIndex" to pageIndex,
                    "pageSize" to pageSize,
                    "totalCount" to all.size,
                    "carBlackList" to page(all, pageIndex, pageSize)
                )
            )
        }
    
    @PostMapping("/api/blacklist/AddCarBlackInfo")
    fun addBlacklist(@RequestHeader version: String?, @RequestBody request: JsonNode) =
        handle(version, request, "addCarBlackInfo") {
            success(
                store.addBlacklist(
                    request.requiredText("plateNo"), request.requiredText("reason"), request.textValue("remark") ?: ""
                )
            )
        }
    
    @PostMapping("/api/blacklist/ModifyCarBlackInfo")
    fun modifyBlacklist(@RequestHeader version: String?, @RequestBody request: JsonNode) =
        handle(version, request, "modifyCarBlackInfo") {
            success(
                store.updateBlacklist(
                    request.requiredLong("id"),
                    request.requiredText("plateNo"),
                    request.requiredText("reason"),
                    request.textValue("remark") ?: ""
                )
            )
        }
    
    @PostMapping("/api/blacklist/DelCarBlackInfo")
    fun deleteBlacklist(@RequestHeader version: String?, @RequestBody request: JsonNode) =
        handle(version, request, "delCarBlackInfo") {
            val id = request.longValue("id")
            val plateNo = request.textValue("plateNo")
            require(id != null || !plateNo.isNullOrBlank()) { "id或plateNo至少提供一个" }
            store.deleteBlacklist(id, plateNo)
            success(emptyMap<String, Any>())
        }
    
    @PostMapping("/mock/capture.jpg")
    fun capture(): ResponseEntity<ByteArray> = ResponseEntity.ok().body(ByteArray(0))
    
    private fun <T> handle(
        version: String?, request: JsonNode, serviceCode: String, action: () -> T
    ): ResponseEntity<Map<String, Any?>> {
        validate(version, request, serviceCode)
        return ResponseEntity.ok(
            mapOf(
                "resCode" to "0", "resMsg" to "成功", "data" to objectMapper.writeValueAsString(action())
            )
        )
    }
    
    private fun validate(version: String?, request: JsonNode, serviceCode: String) {
        require(version == properties.version) { "version必须为${properties.version}" }
        require(request.isObject) { "请求体必须为JSON对象" }
        require(request.requiredInt("appId") == properties.appId) { "appId无效" }
        require(request.requiredText("parkId") == properties.parkId) { "parkId无效" }
        require(request.requiredText("serviceCode") == serviceCode) { "serviceCode无效" }
        require(
            request.requiredText("reqId").let { runCatching { UUID.fromString(it) }.isSuccess }) { "reqId必须为UUID" }
        require(request.requiredLong("ts") > 0) { "ts无效" }
        val provided = request.requiredText("key")
        require(provided == sign(request)) { "签名无效" }
    }
    
    private fun sign(request: JsonNode): String {
        val fields = TreeMap<String, String>()
        request.properties().forEach { entry ->
            val name = entry.key
            val value = entry.value
            if (name == "key" || name == "appId" || value.isNull || value.isContainer || (value.isString && value.asString()
                    .isEmpty())
            ) return@forEach
            fields[name] = value.asString()
        }
        val plain = fields.entries.joinToString("&") { "${it.key}=${it.value}" } + "&" + properties.appSecret
        return MessageDigest.getInstance("MD5").digest(plain.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02X".format(it.toInt() and 0xff) }
    }
    
    private fun JsonNode.requiredText(name: String): String =
        textValue(name)?.takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("字段不能为空: $name")
    
    private fun JsonNode.requiredInt(name: String): Int =
        intValue(name) ?: throw IllegalArgumentException("字段不能为空: $name")
    
    private fun JsonNode.requiredLong(name: String): Long =
        longValue(name) ?: throw IllegalArgumentException("字段不能为空: $name")
    
    private fun JsonNode.requiredJson(name: String): JsonNode = requiredText(name).let(objectMapper::readTree)
    private fun JsonNode.textValue(name: String): String? = get(name)?.takeUnless { it.isNull }?.asString()
    private fun JsonNode.intValue(name: String): Int? = get(name)?.takeUnless { it.isNull }?.asInt()
    private fun JsonNode.longValue(name: String): Long? = get(name)?.takeUnless { it.isNull }?.asLong()
    private fun <T> page(values: List<T>, pageIndex: Int, pageSize: Int): List<T> =
        values.drop((pageIndex - 1) * pageSize).take(pageSize)
    
    private fun <T> success(value: T): T = value
}

@RestControllerAdvice
class KeytopMockExceptionHandler {
    @ExceptionHandler(IllegalArgumentException::class)
    fun badRequest(exception: IllegalArgumentException): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(mapOf("resCode" to "400", "resMsg" to (exception.message ?: "请求无效"), "data" to "{}"))
    
    @ExceptionHandler(Exception::class)
    fun serverError(exception: Exception): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(mapOf("resCode" to "500", "resMsg" to (exception.message ?: "服务异常"), "data" to "{}"))
}
