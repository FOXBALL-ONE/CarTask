package top.itneko.keytop

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.*

@RestController
@RequestMapping("/unite-api")
class KeytopMockController(
    private val properties: KeytopMockProperties,
    private val objectMapper: ObjectMapper,
    private val service: KeytopMockService,
) {
    @Deprecated("Use KeytopMockService instead")
    private val store = KeytopMockStore(objectMapper)
    
    @PostMapping("/api/wec/GetCarCardList")
    fun getCarCardList(@RequestHeader version: String?, @RequestBody request: JsonNode) =
        handle(version, request, "getCarCardList") {
            val pageIndex = request.requiredInt("pageIndex")
            val pageSize = request.requiredInt("pageSize")
            require(pageIndex > 0 && pageSize > 0) { "pageIndex和pageSize必须大于0" }
            val all = service.listCards()
            success(
                mapOf(
                    "pageIndex" to pageIndex,
                    "carCardList" to page(cardToView(all), pageIndex, pageSize),
                    "pageSize" to pageSize,
                    "totalCount" to all.size
                )
            )
        }
    
    @PostMapping("/api/wec/AddCarCardNo")
    fun addCarCard(@RequestHeader version: String?, @RequestBody request: JsonNode) =
        handle(version, request, "addCarCardNo") {
            val card = service.addCard(
                request.requiredJson("cardInfo"),
                request.requiredJson("carLotList"),
                request.requiredJson("plateNoInfo")
            )
            success(cardToView(card))
        }

    @PostMapping("/api/wec/GetCarCardInfo")
    fun getCarCardInfo(@RequestHeader version: String?, @RequestBody request: JsonNode) =
        handle(version, request, "getCarCardInfo") {
            val card =
                request.longValue("cardId")?.let(service::getCard) ?: service.getCardByPlate(request.requiredText("plateNo"))
            success(cardToView(card))
        }

    @PostMapping("/api/wec/ModifyCarCardNo")
    fun modifyCarCard(@RequestHeader version: String?, @RequestBody request: JsonNode) =
        handle(version, request, "modifyCarCardNo") {
            val card = service.updateCard(
                request.requiredJson("cardInfo"),
                request.requiredJson("carLotList"),
                request.requiredJson("plateNoInfo")
            )
            success(cardToView(card))
        }

    @PostMapping("/api/wec/DelCarCardInfo")
    fun deleteCarCard(@RequestHeader version: String?, @RequestBody request: JsonNode) =
        handle(version, request, "delCarCardInfo") {
            service.deleteCard(request.requiredLong("cardId"))
            success(emptyMap<String, Any>())
        }

    @PostMapping("/api/wec/PayCarCardFee")
    fun payCarCard(@RequestHeader version: String?, @RequestBody request: JsonNode) =
        handle(version, request, "payCarCardFee") {
            val cardId = request.requiredLong("cardId")
            service.updateValidity(cardId, request.requiredText("validFrom"), request.requiredText("validTo"))
            success(mapOf("cardId" to cardId, "orderNo" to request.requiredText("orderNo")))
        }

    @PostMapping("/api/wec/RefundCarCardFee")
    fun refundCarCard(@RequestHeader version: String?, @RequestBody request: JsonNode) =
        handle(version, request, "refundCarCardFee") {
            val cardId = request.requiredLong("cardId")
            service.updateValidity(cardId, request.requiredText("validFrom"), request.requiredText("validTo"))
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
            success(service.getUserCard(request.requiredText("plateNo")))
        }

    @PostMapping("/api/wec/GetCarInoutInfo")
    fun getCarInoutInfo(@RequestHeader version: String?, @RequestBody request: JsonNode) =
        handle(version, request, "getCarInoutInfo") {
            val pageIndex = request.requiredInt("pageIndex")
            val pageSize = request.requiredInt("pageSize")
            require(pageIndex > 0 && pageSize > 0) { "pageIndex和pageSize必须大于0" }
            val records = service.inoutRecords(request.textValue("plateNo"))
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
            val all = service.blacklistList(request.textValue("plateNo"))
            success(
                mapOf(
                    "pageIndex" to pageIndex,
                    "pageSize" to pageSize,
                    "totalCount" to all.size,
                    "carBlackList" to page(all.map(::blacklistItemToView), pageIndex, pageSize)
                )
            )
        }

    @PostMapping("/api/blacklist/AddCarBlackInfo")
    fun addBlacklist(@RequestHeader version: String?, @RequestBody request: JsonNode) =
        handle(version, request, "addCarBlackInfo") {
            success(
                blacklistItemToView(
                    service.addBlacklist(
                        request.requiredText("plateNo"), request.requiredText("reason"), request.textValue("remark") ?: ""
                    )
                )
            )
        }

    @PostMapping("/api/blacklist/ModifyCarBlackInfo")
    fun modifyBlacklist(@RequestHeader version: String?, @RequestBody request: JsonNode) =
        handle(version, request, "modifyCarBlackInfo") {
            success(
                blacklistItemToView(
                    service.updateBlacklist(
                        request.requiredLong("id"),
                        request.requiredText("plateNo"),
                        request.requiredText("reason"),
                        request.textValue("remark") ?: ""
                    )
                )
            )
        }

    @PostMapping("/api/blacklist/DelCarBlackInfo")
    fun deleteBlacklist(@RequestHeader version: String?, @RequestBody request: JsonNode) =
        handle(version, request, "delCarBlackInfo") {
            val id = request.longValue("id")
            val plateNo = request.textValue("plateNo")
            require(id != null || !plateNo.isNullOrBlank()) { "id或plateNo至少提供一个" }
            service.deleteBlacklist(id, plateNo)
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
                "resCode" to "0", "resMsg" to "成功", "data" to action()
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
        request.fields().forEach { (name, value) ->
            if (name == "key" || name == "appId" || value.isNull || value.isContainerNode() || (value.isTextual && value.asText()
                    .isEmpty())
            ) return@forEach
            fields[name] = value.asText()
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
    private fun JsonNode.textValue(name: String): String? = get(name)?.takeUnless { it.isNull }?.asText()
    private fun JsonNode.intValue(name: String): Int? = get(name)?.takeUnless { it.isNull }?.asInt()
    private fun JsonNode.longValue(name: String): Long? = get(name)?.takeUnless { it.isNull }?.asLong()
    private fun <T> page(values: List<T>, pageIndex: Int, pageSize: Int): List<T> =
        values.drop((pageIndex - 1) * pageSize).take(pageSize)

    private fun <T> success(value: T): T = value

    private fun cardToView(card: CarCard): Map<String, Any?> = mapOf(
        "cardId" to card.cardId,
        "cardName" to card.cardName,
        "updateUser" to card.updateUser,
        "userName" to card.userName,
        "useName" to card.useName,
        "tel" to card.tel,
        "email" to card.email,
        "roomId" to card.roomId,
        "remak" to card.remak,
        "contact" to card.contact,
        "assist" to card.assist,
        "validCount" to card.validCount,
        "fullCarNoStr" to card.fullCarNoStr,
        "merchantId" to card.merchantId,
        "imageUrl" to card.imageUrl,
        "lotCount" to card.lotCount,
        "cardState" to card.cardState,
        "state" to card.state,
        "lastUpdateTime" to card.lastUpdateTime.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
        "effectiveTime" to card.effectiveTime,
        "validFrom" to card.validFrom,
        "validTo" to card.validTo,
        "plateNoInfo" to card.plateNoInfo.map { mapOf(
            "id" to it.id,
            "cardId" to it.cardId,
            "plateNo" to it.plateNo,
            "etcNo" to it.etcNo,
            "remark" to it.remark,
            "plateState" to it.plateState,
        ) },
        "carLotList" to card.carLotList.map { mapOf(
            "id" to it.id,
            "cardId" to it.cardId,
            "lotName" to it.lotName,
            "carType" to it.carType,
            "sequence" to it.sequence,
            "areaName" to it.areaName,
            "areaId" to it.areaId(),
            "lotCount" to it.lotCount,
        ) },
    )

    private fun cardToView(cards: List<CarCard>): List<Map<String, Any?>> = cards.map(::cardToView)

    private fun blacklistItemToView(item: BlacklistItem): Map<String, Any?> = mapOf(
        "id" to item.id,
        "plateNo" to item.plateNo,
        "reason" to item.reason,
        "remark" to item.remark,
        "createTime" to item.createTime.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
    )
}

@RestControllerAdvice
class KeytopMockExceptionHandler {
    @ExceptionHandler(IllegalArgumentException::class)
    fun badRequest(exception: IllegalArgumentException): Map<String, Any?> =
        mapOf("resCode" to "400", "resMsg" to (exception.message ?: "请求无效"), "data" to emptyMap<String, Any>())

    @ExceptionHandler(Exception::class)
    fun serverError(exception: Exception): Map<String, Any?> {
        exception.printStackTrace()
        return mapOf("resCode" to "500", "resMsg" to (exception.message ?: "服务异常"), "data" to emptyMap<String, Any>())
    }
}
