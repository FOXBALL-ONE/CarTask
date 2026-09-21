package top.itneko.keytop

import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicLong

class KeytopMockStore(private val objectMapper: ObjectMapper) {
    private val cardIdSequence = AtomicLong(740)
    private val itemIdSequence = AtomicLong(1)
    private val cards = linkedMapOf<Long, MutableMap<String, Any?>>()
    private val blacklists = linkedMapOf<Long, MutableMap<String, Any?>>()
    
    @Synchronized
    fun listCards(): List<Map<String, Any?>> = cards.values.map { cardView(it) }
    
    @Synchronized
    fun addCard(cardInfo: JsonNode, lots: JsonNode, plates: JsonNode): Map<String, Any?> {
        val cardId = cardIdSequence.incrementAndGet()
        val card = cardFrom(cardInfo, cardId)
        card["carLotList"] = lotsFrom(lots, cardId)
        card["plateNoInfo"] = platesFrom(plates, cardId)
        cards[cardId] = card
        return cardView(card)
    }
    
    @Synchronized
    fun updateCard(cardInfo: JsonNode, lots: JsonNode, plates: JsonNode): Map<String, Any?> {
        val cardId = cardInfo.longValue("cardId") ?: throw IllegalArgumentException("cardInfo.cardId不能为空")
        val card = cards[cardId] ?: throw IllegalArgumentException("月卡不存在: $cardId")
        val updated = cardFrom(cardInfo, cardId)
        updated["carLotList"] = lotsFrom(lots, cardId)
        updated["plateNoInfo"] = platesFrom(plates, cardId)
        cards[cardId] = updated
        return cardView(updated)
    }
    
    @Synchronized
    fun card(cardId: Long): Map<String, Any?> = cards[cardId]?.let(::cardView)
        ?: throw IllegalArgumentException("月卡不存在: $cardId")
    
    @Synchronized
    fun cardByPlate(plateNo: String): Map<String, Any?> = cards.values.firstOrNull { card ->
        plates(card).any { it["plateNo"] == plateNo }
    }?.let(::cardView) ?: throw IllegalArgumentException("车牌未找到月卡: $plateNo")
    
    @Synchronized
    fun deleteCard(cardId: Long) {
        if (cards.remove(cardId) == null) throw IllegalArgumentException("月卡不存在: $cardId")
    }
    
    @Synchronized
    fun updateValidity(cardId: Long, validFrom: String, validTo: String) {
        val card = cards[cardId] ?: throw IllegalArgumentException("月卡不存在: $cardId")
        card["validFrom"] = validFrom
        card["validTo"] = validTo
        card["effectiveTime"] = validFrom
    }
    
    @Synchronized
    fun blacklistList(plateNo: String?): List<Map<String, Any?>> = blacklists.values
        .filter { plateNo.isNullOrBlank() || it["plateNo"] == plateNo }
        .map { it.toMap() }
    
    @Synchronized
    fun addBlacklist(plateNo: String, reason: String, remark: String): Map<String, Any?> {
        val id = itemIdSequence.getAndIncrement()
        val item = mutableMapOf<String, Any?>("id" to id, "plateNo" to plateNo, "reason" to reason, "remark" to remark)
        blacklists[id] = item
        return item.toMap()
    }
    
    @Synchronized
    fun updateBlacklist(id: Long, plateNo: String, reason: String, remark: String): Map<String, Any?> {
        if (!blacklists.containsKey(id)) throw IllegalArgumentException("黑名单不存在: $id")
        val item = mutableMapOf<String, Any?>("id" to id, "plateNo" to plateNo, "reason" to reason, "remark" to remark)
        blacklists[id] = item
        return item.toMap()
    }
    
    @Synchronized
    fun deleteBlacklist(id: Long?, plateNo: String?): Boolean {
        val ids =
            blacklists.values.filter { (id == null || it["id"] == id) && (plateNo.isNullOrBlank() || it["plateNo"] == plateNo) }
                .mapNotNull { it["id"] as? Long }
        ids.forEach(blacklists::remove)
        return ids.isNotEmpty()
    }
    
    fun userCard(plateNo: String): Map<String, Any?> {
        val card = cardByPlate(plateNo)
        val lots = plates(card).filter { it["plateNo"] == plateNo }.map {
            mapOf(
                "areaName" to "模拟区域",
                "carLotIds" to 1,
                "cardType" to 1,
                "cardTypeName" to "月卡",
                "endTime" to (card["validTo"] ?: "2099-12-31"),
                "lotCount" to 1,
                "packageModel" to 1,
                "startTime" to (card["validFrom"] ?: "2026-01-01"),
                "validLabel" to "有效",
                "validValue" to 1,
            )
        }
        return mapOf(
            "carLotList" to lots,
            "carNo" to plateNo,
            "cardId" to card["cardId"].toString(),
            "cardNo" to "",
            "name" to (card["useName"] ?: ""),
            "setMenu" to "",
            "tel" to (card["tel"] ?: ""),
        )
    }
    
    fun inoutRecords(plateNo: String?): List<Map<String, Any?>> = listOf(
        mapOf(
            "plateNo" to (plateNo ?: firstPlate() ?: "闽A12345"),
            "capFlag" to "1",
            "capTime" to LocalDateTime.now().format(TIME),
            "imgName" to "capture.jpg",
            "imgType" to 2,
            "imgInfo" to "http://127.0.0.1:8090/mock/capture.jpg",
            "capPlace" to "模拟出口",
            "carType" to 1,
            "carColor" to "黑",
            "carStyle" to "0",
            "carBrand" to "模拟车辆",
            "cardNo" to "",
            "passType" to 0,
            "passRemark" to "",
            "trafficId" to "mock-traffic-1",
            "nodeId" to "1",
            "carSerial" to "000001",
            "carOwnerName" to "",
            "operator" to "0",
            "operName" to "",
            "serialType" to "0",
        ),
    )
    
    private fun cardFrom(node: JsonNode, cardId: Long): MutableMap<String, Any?> = mutableMapOf(
        "cardName" to node.textValue("cardName"),
        "updateUser" to "mock",
        "userName" to node.textValue("useName"),
        "validCount" to "1",
        "fullCarNoStr" to "",
        "merchantId" to "",
        "cardId" to cardId,
        "imageUrl" to "",
        "lotCount" to 1,
        "useName" to node.textValue("useName"),
        "tel" to node.textValue("tel"),
        "cardState" to 1,
        "state" to 1,
        "email" to "",
        "lastUpdateTime" to LocalDateTime.now().format(TIME),
        "roomId" to node.textValue("roomId"),
        "remak" to (node.textValue("remak") ?: ""),
        "contact" to (node.textValue("contact") ?: ""),
        "assist" to (node.textValue("assist") ?: ""),
        "effectiveTime" to "",
        "validFrom" to "",
        "validTo" to "",
    )
    
    private fun lotsFrom(node: JsonNode, cardId: Long): List<Map<String, Any?>> =
        node.toList().asSequence().map { item ->
            mapOf(
                "lotName" to item.textValue("lotName"),
                "carType" to (item.intValue("carType") ?: 1),
                "sequence" to (item.intValue("sequence") ?: 1),
                "areaName" to item.textValue("areaName"),
                "areaId" to item["areaId"]?.toList()?.map { id -> id.asInt() }.orEmpty(),
                "lotCount" to (item.intValue("lotCount") ?: 1),
                "id" to (item.longValue("id") ?: itemIdSequence.getAndIncrement()),
                "cardId" to cardId,
            )
        }.toList()
    
    private fun platesFrom(node: JsonNode, cardId: Long): List<Map<String, Any?>> =
        node.toList().asSequence().map { item ->
            mapOf(
                "plateNo" to item.textValue("plateNo"),
                "etcNo" to (item.textValue("etcNo") ?: ""),
                "remark" to (item.textValue("remark") ?: ""),
                "id" to (item.longValue("id") ?: itemIdSequence.getAndIncrement()),
                "plateState" to (item.intValue("plateState") ?: 1),
                "cardId" to cardId,
            )
        }.toList()
    
    private fun cardView(card: Map<String, Any?>): Map<String, Any?> = card.toMutableMap().apply {
        this["plateNoInfo"] = objectMapper.writeValueAsString(plates(card))
        this["carLotList"] = objectMapper.writeValueAsString(card["carLotList"] ?: emptyList<Any>())
    }
    
    @Suppress("UNCHECKED_CAST")
    private fun plates(card: Map<String, Any?>): List<Map<String, Any?>> =
        card["plateNoInfo"] as? List<Map<String, Any?>> ?: emptyList()
    
    private fun firstPlate(): String? = cards.values.asSequence().flatMap { plates(it).asSequence() }
        .mapNotNull { it["plateNo"] as? String }.firstOrNull()
    
    private fun JsonNode.textValue(name: String): String? = get(name)?.takeUnless { it.isNull }?.asString()
    private fun JsonNode.intValue(name: String): Int? = get(name)?.takeUnless { it.isNull }?.asInt()
    private fun JsonNode.longValue(name: String): Long? = get(name)?.takeUnless { it.isNull }?.asLong()
    
    private companion object {
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
