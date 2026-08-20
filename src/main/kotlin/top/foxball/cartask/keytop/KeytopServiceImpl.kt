package top.foxball.cartask.keytop

import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import tools.jackson.databind.ObjectMapper
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class KeytopServiceImpl(
    restClientBuilder: RestClient.Builder,
    private val properties: KeytopProperties,
    private val objectMapper: ObjectMapper,
) : KeytopService {
    private val requestFactory = SimpleClientHttpRequestFactory().apply {
        setConnectTimeout(properties.timeout)
        setReadTimeout(properties.timeout)
    }

    private val restClient = restClientBuilder
        .baseUrl(properties.baseUrl.trimEnd('/'))
        .requestFactory(requestFactory)
        .build()

    override fun getCarCardList(pageIndex: Int, pageSize: Int): KeytopResponse = post(
        path = "/api/wec/GetCarCardList",
        serviceCode = "getCarCardList",
        business = mapOf("pageIndex" to pageIndex, "pageSize" to pageSize),
    )

    override fun addCarCardNo(
        userId: Long,
        userName: String,
        cardInfo: KeytopCardInfo,
        carLotList: List<KeytopCarLot>,
        plateNoInfo: List<KeytopPlateNo>,
    ): KeytopResponse = addCarCardNo(
        userId = userId,
        userName = userName,
        cardInfo = objectMapper.writeValueAsString(cardInfo.copy(cardId = null)),
        carLotList = objectMapper.writeValueAsString(carLotList.map { it.copy(id = null) }),
        plateNoInfo = objectMapper.writeValueAsString(plateNoInfo.map { it.copy(id = null, plateState = null) }),
    )

    override fun addCarCardNo(
        userId: Long,
        userName: String,
        cardInfo: String,
        carLotList: String,
        plateNoInfo: String,
    ): KeytopResponse = post(
        path = "/api/wec/AddCarCardNo",
        serviceCode = "addCarCardNo",
        business = mapOf(
            "userId" to userId,
            "userName" to userName,
            "cardInfo" to cardInfo,
            "carLotList" to carLotList,
            "plateNoInfo" to plateNoInfo,
        ),
    )

    override fun getCarCardInfo(cardId: Long): KeytopResponse = post(
        path = "/api/wec/GetCarCardInfo",
        serviceCode = "getCarCardInfo",
        business = mapOf("cardId" to cardId),
    )

    override fun getCarCardInfo(plateNo: String): KeytopResponse = post(
        path = "/api/wec/GetCarCardInfo",
        serviceCode = "getCarCardInfo",
        business = mapOf("plateNo" to plateNo),
    )

    override fun modifyCarCardNo(
        userId: Long,
        userName: String,
        cardInfo: KeytopCardInfo,
        carLotList: List<KeytopCarLot>,
        plateNoInfo: List<KeytopPlateNo>,
    ): KeytopResponse = modifyCarCardNo(
        userId = userId,
        userName = userName,
        cardInfo = objectMapper.writeValueAsString(cardInfo),
        carLotList = objectMapper.writeValueAsString(carLotList),
        plateNoInfo = objectMapper.writeValueAsString(plateNoInfo),
    )

    override fun modifyCarCardNo(
        userId: Long,
        userName: String,
        cardInfo: String,
        carLotList: String,
        plateNoInfo: String,
    ): KeytopResponse = post(
        path = "/api/wec/ModifyCarCardNo",
        serviceCode = "modifyCarCardNo",
        business = mapOf(
            "userId" to userId,
            "userName" to userName,
            "cardInfo" to cardInfo,
            "carLotList" to carLotList,
            "plateNoInfo" to plateNoInfo,
        ),
    )

    override fun delCarCardInfo(cardId: Long): KeytopResponse = post(
        path = "/api/wec/DelCarCardInfo",
        serviceCode = "delCarCardInfo",
        business = mapOf("cardId" to cardId),
    )

    override fun payCarCardFee(request: KeytopPayCarCardFeeRequest): KeytopResponse = post(
        path = "/api/wec/PayCarCardFee",
        serviceCode = "payCarCardFee",
        business = mapOf(
            "userId" to request.userId,
            "userName" to request.userName,
            "cardId" to request.cardId,
            "orderNo" to request.orderNo,
            "carType" to request.carType,
            "payChannel" to request.payChannel,
            "chargeMethod" to request.chargeMethod,
            "chargeNumber" to request.chargeNumber,
            "amount" to request.amount,
            "freeNumber" to request.freeNumber,
            "validFrom" to request.validFrom.format(PROTOCOL_TIME),
            "validTo" to request.validTo.format(PROTOCOL_TIME),
            "createTime" to request.createTime.format(PROTOCOL_TIME),
        ),
    )

    override fun payCarCardFee(
        userId: Long,
        userName: String,
        cardId: Long,
        carType: Int,
        validFrom: LocalDateTime,
        validTo: LocalDateTime,
        createTime: LocalDateTime,
    ): KeytopResponse = payCarCardFee(
        KeytopPayCarCardFeeRequest(userId, userName, cardId, carType, validFrom, validTo, createTime),
    )

    override fun refundCarCardFee(request: KeytopRefundCarCardFeeRequest): KeytopResponse {
        require(!request.validTo.isBefore(request.validFrom)) { "validTo must not be before validFrom" }
        val refundNumber = ChronoUnit.DAYS.between(request.validFrom.toLocalDate(), request.validTo.toLocalDate()) + 1
        return post(
            path = "/api/wec/RefundCarCardFee",
            serviceCode = "refundCarCardFee",
            business = mapOf(
                "userId" to request.userId,
                "userName" to request.userName,
                "cardId" to request.cardId,
                "orderNo" to request.orderNo,
                "carType" to request.carType,
                "payChannel" to request.payChannel,
                "refundMethod" to request.refundMethod,
                "refundNumber" to refundNumber,
                "amount" to request.amount,
                "freeNumber" to request.freeNumber,
                "validFrom" to request.validFrom.format(PROTOCOL_TIME),
                "validTo" to request.validTo.format(PROTOCOL_TIME),
                "createTime" to request.createTime.format(PROTOCOL_TIME),
                "remark" to request.remark,
            ),
        )
    }

    override fun refundCarCardFee(
        userId: Long,
        userName: String,
        cardId: Long,
        carType: Int,
        validFrom: LocalDateTime,
        validTo: LocalDateTime,
        createTime: LocalDateTime,
        remark: String,
    ): KeytopResponse = refundCarCardFee(
        KeytopRefundCarCardFeeRequest(userId, userName, cardId, carType, validFrom, validTo, createTime, remark = remark),
    )

    override fun getCardInfoByUser(plateNo: String): KeytopResponse = post(
        path = "/api/carCard/GetCardInfoByUser",
        serviceCode = "getCardInfoByUser",
        business = mapOf("plateNo" to plateNo),
    )

    override fun getCarInoutInfo(
        pageIndex: Int,
        pageSize: Int,
        plateNo: String?,
        startTime: LocalDateTime?,
        endTime: LocalDateTime?,
    ): KeytopResponse = post(
        path = "/api/wec/GetCarInoutInfo",
        serviceCode = "getCarInoutInfo",
        business = mapOf(
            "pageIndex" to pageIndex,
            "pageSize" to pageSize,
            "plateNo" to plateNo,
            "startTime" to startTime?.format(PROTOCOL_TIME),
            "endTime" to endTime?.format(PROTOCOL_TIME),
        ),
    )

    override fun getParkingPlaceArea(): KeytopResponse = post(
        path = "/api/wec/GetParkingPlaceArea",
        serviceCode = "getParkingPlaceArea",
        business = emptyMap(),
    )

    override fun getDictList(dictType: String?): KeytopResponse = post(
        path = "/api/wec/GetDictList",
        serviceCode = "GetDictDataList",
        business = mapOf("dictType" to dictType),
    )

    override fun queryCarBlackInfo(pageIndex: Int, pageSize: Int, plateNo: String?): KeytopResponse = post(
        path = "/api/blacklist/QueryCarBlackInfo",
        serviceCode = "queryCarBlackInfo",
        business = mapOf("pageIndex" to pageIndex, "pageSize" to pageSize, "plateNo" to plateNo),
    )

    override fun addCarBlackInfo(plateNo: String, reason: String, remark: String): KeytopResponse = post(
        path = "/api/blacklist/AddCarBlackInfo",
        serviceCode = "addCarBlackInfo",
        business = mapOf("plateNo" to plateNo, "reason" to reason, "remark" to remark),
    )

    override fun modifyCarBlackInfo(id: Long, plateNo: String, reason: String, remark: String): KeytopResponse = post(
        path = "/api/blacklist/ModifyCarBlackInfo",
        serviceCode = "modifyCarBlackInfo",
        business = mapOf("id" to id, "plateNo" to plateNo, "reason" to reason, "remark" to remark),
    )

    override fun delCarBlackInfo(id: Long?, plateNo: String?): KeytopResponse {
        require(id != null || !plateNo.isNullOrBlank()) { "id or plateNo must be provided" }
        return post(
            path = "/api/blacklist/DelCarBlackInfo",
            serviceCode = "delCarBlackInfo",
            business = mapOf("id" to id, "plateNo" to plateNo),
        )
    }

    private fun post(path: String, serviceCode: String, business: Map<String, Any?>): KeytopResponse {
        val request = LinkedHashMap<String, Any?>()
        request["appId"] = properties.appId
        request["parkId"] = properties.parkId
        request["serviceCode"] = serviceCode
        request["ts"] = System.currentTimeMillis()
        request["reqId"] = UUID.randomUUID().toString()
        business.forEach { (name, value) ->
            if (value != null) request[name] = value
        }
        request["key"] = KeytopSignature.paramsSign(request, properties.appSecret)

        val body = restClient.post()
            .uri(path)
            .contentType(MediaType.APPLICATION_JSON)
            .header("version", properties.version)
            .body(request)
            .retrieve()
            .body(String::class.java)
            ?: throw IllegalStateException("Keytop returned an empty response")
        val json = objectMapper.readTree(body)
        return KeytopResponse(
            code = json.get("code")?.asInt(),
            message = json.get("message")?.asString(),
            data = json.get("data"),
        )
    }

    private companion object {
        val PROTOCOL_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
