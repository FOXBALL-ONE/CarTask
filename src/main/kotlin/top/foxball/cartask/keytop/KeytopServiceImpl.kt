package top.foxball.cartask.keytop

import org.springframework.http.MediaType
import org.springframework.http.HttpStatusCode
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

    override fun getCarCardList(pageIndex: Int, pageSize: Int): KeytopResponse {
        requirePage(pageIndex, pageSize)
        return post(
            path = "/api/wec/GetCarCardList",
            serviceCode = "getCarCardList",
            business = mapOf("pageIndex" to pageIndex, "pageSize" to pageSize),
        )
    }

    override fun addCarCardNo(
        userId: Long,
        userName: String,
        cardInfo: KeytopCardInfo,
        carLotList: List<KeytopCarLot>,
        plateNoInfo: List<KeytopPlateNo>,
    ): KeytopResponse {
        require(userId > 0) { "userId must be greater than 0" }
        require(userName.isNotBlank()) { "userName must not be blank" }
        return addCarCardNo(
            userId = userId,
            userName = userName,
            cardInfo = objectMapper.writeValueAsString(cardInfo.copy(cardId = null)),
            carLotList = objectMapper.writeValueAsString(carLotList.map { it.copy(id = null) }),
            plateNoInfo = objectMapper.writeValueAsString(plateNoInfo.map { it.copy(id = null, plateState = null) }),
        )
    }

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

    override fun getCarCardInfo(cardId: Long): KeytopResponse {
        require(cardId > 0) { "cardId must be greater than 0" }
        return post(
            path = "/api/wec/GetCarCardInfo",
            serviceCode = "getCarCardInfo",
            business = mapOf("cardId" to cardId),
        )
    }

    override fun getCarCardInfo(plateNo: String): KeytopResponse {
        require(plateNo.isNotBlank()) { "plateNo must not be blank" }
        return post(
            path = "/api/wec/GetCarCardInfo",
            serviceCode = "getCarCardInfo",
            business = mapOf("plateNo" to plateNo),
        )
    }

    override fun modifyCarCardNo(
        userId: Long,
        userName: String,
        cardInfo: KeytopCardInfo,
        carLotList: List<KeytopCarLot>,
        plateNoInfo: List<KeytopPlateNo>,
    ): KeytopResponse {
        require(userId > 0) { "userId must be greater than 0" }
        require(userName.isNotBlank()) { "userName must not be blank" }
        require(cardInfo.cardId != null && cardInfo.cardId > 0) { "cardInfo.cardId must be greater than 0" }
        return modifyCarCardNo(
            userId = userId,
            userName = userName,
            cardInfo = objectMapper.writeValueAsString(cardInfo),
            carLotList = objectMapper.writeValueAsString(carLotList),
            plateNoInfo = objectMapper.writeValueAsString(plateNoInfo),
        )
    }

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

    override fun delCarCardInfo(cardId: Long): KeytopResponse {
        require(cardId > 0) { "cardId must be greater than 0" }
        return post(
            path = "/api/wec/DelCarCardInfo",
            serviceCode = "delCarCardInfo",
            business = mapOf("cardId" to cardId),
        )
    }

    override fun payCarCardFee(request: KeytopPayCarCardFeeRequest): KeytopResponse {
        require(request.userId > 0) { "userId must be greater than 0" }
        require(request.cardId > 0) { "cardId must be greater than 0" }
        require(request.userName.isNotBlank()) { "userName must not be blank" }
        require(request.orderNo.isNotBlank()) { "orderNo must not be blank" }
        require(!request.validTo.isBefore(request.validFrom)) { "validTo must not be before validFrom" }
        require(request.chargeNumber > 0) { "chargeNumber must be greater than 0" }
        require(request.amount >= 0) { "amount must not be negative" }
        require(request.freeNumber >= 0) { "freeNumber must not be negative" }
        return post(
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
    }

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
        require(request.userId > 0) { "userId must be greater than 0" }
        require(request.cardId > 0) { "cardId must be greater than 0" }
        require(request.userName.isNotBlank()) { "userName must not be blank" }
        require(request.orderNo.isNotBlank()) { "orderNo must not be blank" }
        require(!request.validTo.isBefore(request.validFrom)) { "validTo must not be before validFrom" }
        require(request.amount >= 0) { "amount must not be negative" }
        require(request.freeNumber >= 0) { "freeNumber must not be negative" }
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

    override fun getCardInfoByUser(plateNo: String): KeytopResponse {
        require(plateNo.isNotBlank()) { "plateNo must not be blank" }
        return post(
            path = "/api/carCard/GetCardInfoByUser",
            serviceCode = "getCardInfoByUser",
            business = mapOf("plateNo" to plateNo),
        )
    }

    override fun getCarInoutInfo(
        pageIndex: Int,
        pageSize: Int,
        plateNo: String?,
        startTime: LocalDateTime?,
        endTime: LocalDateTime?,
    ): KeytopResponse {
        requirePage(pageIndex, pageSize)
        require(plateNo == null || plateNo.isNotBlank()) { "plateNo must not be blank" }
        require(startTime == null || endTime == null || !endTime.isBefore(startTime)) {
            "endTime must not be before startTime"
        }
        return post(
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
    }

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

    override fun queryCarBlackInfo(pageIndex: Int, pageSize: Int, plateNo: String?): KeytopResponse {
        requirePage(pageIndex, pageSize)
        require(plateNo == null || plateNo.isNotBlank()) { "plateNo must not be blank" }
        return post(
            path = "/api/blacklist/QueryCarBlackInfo",
            serviceCode = "queryCarBlackInfo",
            business = mapOf("pageIndex" to pageIndex, "pageSize" to pageSize, "plateNo" to plateNo),
        )
    }

    override fun addCarBlackInfo(plateNo: String, reason: String, remark: String): KeytopResponse {
        require(plateNo.isNotBlank()) { "plateNo must not be blank" }
        require(reason.isNotBlank()) { "reason must not be blank" }
        return post(
            path = "/api/blacklist/AddCarBlackInfo",
            serviceCode = "addCarBlackInfo",
            business = mapOf("plateNo" to plateNo, "reason" to reason, "remark" to remark),
        )
    }

    override fun modifyCarBlackInfo(id: Long, plateNo: String, reason: String, remark: String): KeytopResponse {
        require(id > 0) { "id must be greater than 0" }
        require(plateNo.isNotBlank()) { "plateNo must not be blank" }
        require(reason.isNotBlank()) { "reason must not be blank" }
        return post(
            path = "/api/blacklist/ModifyCarBlackInfo",
            serviceCode = "modifyCarBlackInfo",
            business = mapOf("id" to id, "plateNo" to plateNo, "reason" to reason, "remark" to remark),
        )
    }

    override fun delCarBlackInfo(id: Long?, plateNo: String?): KeytopResponse {
        require(id != null || !plateNo.isNullOrBlank()) { "id or plateNo must be provided" }
        require(id == null || id > 0) { "id must be greater than 0" }
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

        val body = try {
            restClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .header("version", properties.version)
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError) { _, response ->
                    throw IllegalStateException("Keytop HTTP request failed with status ${response.statusCode}")
                }
                .body(String::class.java)
        } catch (exception: IllegalStateException) {
            throw exception
        } catch (exception: RuntimeException) {
            throw IllegalStateException("Keytop request failed for $serviceCode", exception)
        } ?: throw IllegalStateException("Keytop returned an empty response")
        val json = try {
            objectMapper.readTree(body)
        } catch (exception: RuntimeException) {
            throw IllegalStateException("Keytop returned invalid JSON for $serviceCode", exception)
        }
        require(json.isObject()) { "Keytop returned a non-object response" }
        return KeytopResponse(
            code = (json.get("code") ?: json.get("resCode"))?.asInt(),
            message = (json.get("message") ?: json.get("resMsg"))?.asString(),
            data = json.get("data"),
        )
    }

    private fun requirePage(pageIndex: Int, pageSize: Int) {
        require(pageIndex >= 1) { "pageIndex must be greater than 0" }
        require(pageSize >= 1) { "pageSize must be greater than 0" }
    }

    private companion object {
        val PROTOCOL_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
