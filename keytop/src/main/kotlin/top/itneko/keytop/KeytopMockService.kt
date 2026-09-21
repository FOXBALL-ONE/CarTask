package top.itneko.keytop

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
@Transactional
class KeytopMockService(
    private val cardRepository: CarCardRepository,
    private val lotRepository: CarLotRepository,
    private val plateRepository: PlateInfoRepository,
    private val blacklistRepository: BlacklistItemRepository,
) {
    private val mapper = jacksonObjectMapper()
    private val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun listCards(): List<CarCard> = cardRepository.findAll()

    fun addCard(cardInfo: JsonNode, lots: JsonNode, plates: JsonNode): CarCard {
        val cardId = System.currentTimeMillis()
        val carLots = lotsFrom(lots, cardId)
        val plateInfos = platesFrom(plates, cardId)

        val card = CarCard(
            cardId = cardId,
            cardName = textValue(cardInfo, "cardName"),
            userName = textValue(cardInfo, "useName"),
            useName = textValue(cardInfo, "useName"),
            tel = textValue(cardInfo, "tel"),
            roomId = textValue(cardInfo, "roomId"),
            remak = textValue(cardInfo, "remak"),
            contact = textValue(cardInfo, "contact"),
            assist = textValue(cardInfo, "assist"),
            lastUpdateTime = LocalDateTime.now(),
            carLotList = carLots,
            plateNoInfo = plateInfos,
        )
        return cardRepository.save(card)
    }

    fun updateCard(cardInfo: JsonNode, lots: JsonNode, plates: JsonNode): CarCard {
        val cardId = longValue(cardInfo, "cardId")
            ?: throw IllegalArgumentException("cardInfo.cardId 不能为空")
        val card = cardRepository.findByCardId(cardId)
            ?: throw IllegalArgumentException("月卡不存在: $cardId")

        val carLots = lotsFrom(lots, cardId)
        val plateInfos = platesFrom(plates, cardId)

        lotRepository.deleteAll(card.carLotList)
        plateRepository.deleteAll(card.plateNoInfo)

        val updated = card.copy(
            cardName = textValue(cardInfo, "cardName"),
            userName = textValue(cardInfo, "useName"),
            useName = textValue(cardInfo, "useName"),
            tel = textValue(cardInfo, "tel"),
            roomId = textValue(cardInfo, "roomId"),
            remak = textValue(cardInfo, "remak"),
            contact = textValue(cardInfo, "contact"),
            assist = textValue(cardInfo, "assist"),
            lastUpdateTime = LocalDateTime.now(),
            carLotList = carLots,
            plateNoInfo = plateInfos,
        )
        return cardRepository.save(updated)
    }

    fun getCard(cardId: Long): CarCard =
        cardRepository.findByCardId(cardId)
            ?: throw IllegalArgumentException("月卡不存在: $cardId")

    fun getCardByPlate(plateNo: String): CarCard =
        cardRepository.findByPlateNo(plateNo)
            ?: throw IllegalArgumentException("车牌未找到月卡: $plateNo")

    fun deleteCard(cardId: Long) {
        if (cardRepository.findByCardId(cardId) == null) {
            throw IllegalArgumentException("月卡不存在: $cardId")
        }
        cardRepository.deleteById(cardRepository.findByCardId(cardId)!!.id!!)
    }

    fun updateValidity(cardId: Long, validFrom: String, validTo: String) {
        val card = cardRepository.findByCardId(cardId)
            ?: throw IllegalArgumentException("月卡不存在: $cardId")
        val updated = card.copy(
            validFrom = validFrom,
            validTo = validTo,
            effectiveTime = validFrom,
        )
        cardRepository.save(updated)
    }

    fun addBlacklist(plateNo: String, reason: String, remark: String): BlacklistItem {
        val item = BlacklistItem(
            plateNo = plateNo,
            reason = reason,
            remark = remark,
            createTime = LocalDateTime.now(),
        )
        return blacklistRepository.save(item)
    }

    fun updateBlacklist(id: Long, plateNo: String, reason: String, remark: String): BlacklistItem {
        val item = blacklistRepository.findById(id)
            .orElseThrow { IllegalArgumentException("黑名单不存在: $id") }
        val updated = item.copy(
            plateNo = plateNo,
            reason = reason,
            remark = remark,
        )
        return blacklistRepository.save(updated)
    }

    fun blacklistList(plateNo: String?): List<BlacklistItem> {
        return if (plateNo.isNullOrBlank()) {
            blacklistRepository.findAll()
        } else {
            blacklistRepository.findAll().filter { it.plateNo.contains(plateNo) }
        }
    }

    fun deleteBlacklist(id: Long?, plateNo: String?): Boolean {
        require(id != null || !plateNo.isNullOrBlank()) { "id或plateNo至少提供一个" }
        val items = blacklistRepository.findAll().filter { item ->
            (id == null || item.id == id) && (plateNo.isNullOrBlank() || item.plateNo == plateNo)
        }
        if (items.isNotEmpty()) {
            blacklistRepository.deleteAll(items)
            return true
        }
        return false
    }

    fun getUserCard(plateNo: String): Map<String, Any?> {
        val card = getCardByPlate(plateNo)
        val plateInfos = card.plateNoInfo.filter { it.plateNo == plateNo }
        val lots = plateInfos.map {
            mapOf(
                "areaName" to "模拟区域",
                "carLotIds" to 1,
                "cardType" to 1,
                "cardTypeName" to "月卡",
                "endTime" to (card.validTo ?: "2099-12-31"),
                "lotCount" to 1,
                "packageModel" to 1,
                "startTime" to (card.validFrom ?: "2026-01-01"),
                "validLabel" to "有效",
                "validValue" to 1,
            )
        }
        return mapOf(
            "carLotList" to lots,
            "carNo" to plateNo,
            "cardId" to card.cardId.toString(),
            "cardNo" to "",
            "name" to (card.useName ?: ""),
            "setMenu" to "",
            "tel" to (card.tel ?: ""),
        )
    }

    fun inoutRecords(plateNo: String?): List<Map<String, Any?>> {
        val actualPlateNo = plateNo ?: run {
            val firstCard = cardRepository.findAll().firstOrNull()
            firstCard?.plateNoInfo?.firstOrNull()?.plateNo ?: "闽A12345"
        }

        return listOf(
            mapOf(
                "plateNo" to actualPlateNo,
                "capFlag" to "1",
                "capTime" to LocalDateTime.now().format(timeFormatter),
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
            )
        )
    }

    private fun lotsFrom(node: JsonNode, cardId: Long): List<CarLot> {
        val result = mutableListOf<CarLot>()
        node.forEach { item ->
            result.add(CarLot(
                cardId = cardId,
                lotName = item.get("lotName")?.asText() ?: "",
                carType = item.get("carType")?.asInt() ?: 1,
                sequence = item.get("sequence")?.asInt() ?: 1,
                areaName = item.get("areaName")?.asText() ?: "",
                areaIdStr = mapper.writeValueAsString(
                    item.get("areaId")?.map { it.asInt() }?.toList() ?: emptyList<Int>()
                ),
                lotCount = item.get("lotCount")?.asInt() ?: 1,
            ))
        }
        return result
    }

    private fun platesFrom(node: JsonNode, cardId: Long): List<PlateInfo> {
        val result = mutableListOf<PlateInfo>()
        node.forEach { item ->
            result.add(PlateInfo(
                cardId = cardId,
                plateNo = item.get("plateNo")?.asText() ?: "",
                etcNo = item.get("etcNo")?.asText() ?: "",
                remark = item.get("remark")?.asText(),
                plateState = item.get("plateState")?.asInt() ?: 1,
            ))
        }
        return result
    }

    private fun textValue(node: JsonNode, name: String): String? =
        node.get(name)?.takeUnless { it.isNull() }?.asText()

    private fun intValue(node: JsonNode, name: String): Int? =
        node.get(name)?.takeUnless { it.isNull() }?.asInt()

    private fun longValue(node: JsonNode, name: String): Long? =
        node.get(name)?.takeUnless { it.isNull() }?.asLong()
}
