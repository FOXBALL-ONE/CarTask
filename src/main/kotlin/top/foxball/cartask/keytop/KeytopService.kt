package top.foxball.cartask.keytop

import java.time.LocalDateTime

/** 科拓开放平台业务接口。所有方法均同步执行一次平台 POST 请求。 */
interface KeytopService {
    fun getCarCardList(pageIndex: Int = 1, pageSize: Int = 100): KeytopResponse

    fun addCarCardNo(
        userId: Long,
        userName: String,
        cardInfo: KeytopCardInfo,
        carLotList: List<KeytopCarLot>,
        plateNoInfo: List<KeytopPlateNo>,
    ): KeytopResponse

    /** 直接传入平台要求的三个 JSON 字符串，适合已有外部请求模型的调用方。 */
    fun addCarCardNo(
        userId: Long,
        userName: String,
        cardInfo: String,
        carLotList: String,
        plateNoInfo: String,
    ): KeytopResponse

    fun getCarCardInfo(cardId: Long): KeytopResponse

    fun getCarCardInfo(plateNo: String): KeytopResponse

    fun modifyCarCardNo(
        userId: Long,
        userName: String,
        cardInfo: KeytopCardInfo,
        carLotList: List<KeytopCarLot>,
        plateNoInfo: List<KeytopPlateNo>,
    ): KeytopResponse

    fun modifyCarCardNo(
        userId: Long,
        userName: String,
        cardInfo: String,
        carLotList: String,
        plateNoInfo: String,
    ): KeytopResponse

    fun delCarCardInfo(cardId: Long): KeytopResponse

    fun payCarCardFee(request: KeytopPayCarCardFeeRequest): KeytopResponse

    fun payCarCardFee(
        userId: Long,
        userName: String,
        cardId: Long,
        carType: Int,
        validFrom: LocalDateTime,
        validTo: LocalDateTime,
        createTime: LocalDateTime = LocalDateTime.now(),
    ): KeytopResponse

    fun refundCarCardFee(request: KeytopRefundCarCardFeeRequest): KeytopResponse

    fun refundCarCardFee(
        userId: Long,
        userName: String,
        cardId: Long,
        carType: Int,
        validFrom: LocalDateTime,
        validTo: LocalDateTime,
        createTime: LocalDateTime = LocalDateTime.now(),
        remark: String = "有效期缩短",
    ): KeytopResponse

    fun getCardInfoByUser(plateNo: String): KeytopResponse

    fun getCarInoutInfo(
        pageIndex: Int = 1,
        pageSize: Int = 1000,
        plateNo: String? = null,
        startTime: LocalDateTime? = null,
        endTime: LocalDateTime? = null,
    ): KeytopResponse

    fun getParkingPlaceArea(): KeytopResponse

    fun getDictList(dictType: String? = null): KeytopResponse

    fun queryCarBlackInfo(pageIndex: Int = 1, pageSize: Int = 100, plateNo: String? = null): KeytopResponse

    fun addCarBlackInfo(plateNo: String, reason: String, remark: String = ""): KeytopResponse

    fun modifyCarBlackInfo(id: Long, plateNo: String, reason: String, remark: String = ""): KeytopResponse

    fun delCarBlackInfo(id: Long? = null, plateNo: String? = null): KeytopResponse
}
