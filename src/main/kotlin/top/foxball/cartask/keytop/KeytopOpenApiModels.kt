package top.foxball.cartask.keytop

import com.fasterxml.jackson.annotation.JsonInclude
import tools.jackson.databind.JsonNode

/**
 * Wire models reverse-engineered from openapi.yaml.
 *
 * The platform keeps the value of `data` as a JSON string.  The response-body
 * models below therefore represent the first (HTTP) decoding step, while the
 * `*Data` models represent the payload after that string is decoded again.
 */

open class BaseRequest(
    open val appId: Int,
    open val parkId: String,
    open val serviceCode: String,
    open val ts: Long,
    open val reqId: String,
    open val key: String,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class KeytopWireResponse(
    val code: Int? = null,
    val resCode: String? = null,
    val message: String? = null,
    val resMsg: String? = null,
    val data: String? = null,
    val raw: String? = null,
    val httpCode: Int? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class CarCardListResponseBody(
    val resCode: String,
    val resMsg: String,
    val data: String,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class CarCardInfoResponseBody(
    val resCode: String,
    val resMsg: String,
    val data: String,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class CardInfoByUserResponseBody(
    val resCode: String,
    val resMsg: String,
    val data: String,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class CarInoutResponseBody(
    val resCode: String,
    val resMsg: String,
    val data: String,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ParkingPlaceAreaResponseBody(
    val resCode: String,
    val resMsg: String,
    val data: String,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class DictListResponseBody(
    val resCode: String,
    val resMsg: String,
    val data: String,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class CarBlackInfoResponseBody(
    val resCode: String,
    val resMsg: String,
    val data: String,
)

data class CarCardListData(
    val pageIndex: JsonNode,
    val carCardList: List<CarCardData>,
    val pageSize: JsonNode,
    val totalCount: JsonNode,
)

data class CarCardData(
    val cardName: String? = null,
    val updateUser: String? = null,
    val plateNoInfo: String? = null,
    val userName: String? = null,
    val validCount: String? = null,
    val fullCarNoStr: String? = null,
    val merchantId: String? = null,
    val cardId: Long? = null,
    val imageUrl: String? = null,
    val lotCount: Int? = null,
    val useName: String? = null,
    val tel: String? = null,
    val cardState: Int? = null,
    val state: Int? = null,
    val email: String? = null,
    val carLotList: String? = null,
    val lastUpdateTime: String? = null,
    val roomId: String? = null,
    val remak: String? = null,
    val contact: String? = null,
    val assist: String? = null,
    val effectiveTime: String? = null,
)

data class CardInfoByUserData(
    val carLotList: List<CardUserLot>,
    val carNo: String,
    val cardId: String,
    val cardNo: String,
    val name: String,
    val setMenu: String,
    val tel: String,
)

data class CardUserLot(
    val areaName: String? = null,
    val carLotIds: Int? = null,
    val cardType: Int? = null,
    val cardTypeName: String? = null,
    val endTime: String? = null,
    val lotCount: Int? = null,
    val packageModel: Int? = null,
    val startTime: String? = null,
    val validLabel: String? = null,
    val validValue: Int? = null,
)

data class CarInoutData(
    val pageIndex: JsonNode,
    val detailList: List<CarInoutRecord>,
    val pageSize: JsonNode,
    val totalCount: JsonNode,
)

data class CarInoutRecord(
    val plateNo: String,
    val capFlag: String,
    val capTime: String,
    val imgName: String,
    val imgType: Int,
    val imgInfo: String,
    val capPlace: String,
    val carType: Int? = null,
    val carColor: String? = null,
    val carStyle: String? = null,
    val carBrand: String? = null,
    val cardNo: String? = null,
    val passType: Int? = null,
    val passRemark: String? = null,
    val trafficId: String? = null,
    val nodeId: String? = null,
    val carSerial: String? = null,
    val carOwnerName: String? = null,
    val operator: String? = null,
    val operName: String? = null,
    val serialType: String? = null,
)

data class ParkingAreaInfo(
    val areaCode: Int,
    val areaName: String,
    val placeCount: Int,
)

data class ParkingPlaceAreaData(
    val areaInfo: List<ParkingAreaInfo>,
    val parkArea: String,
    val totalPlaceCount: Int,
)

data class DictItem(
    val id: Long,
    val dictType: String,
    val dictName: String,
    val dictKey: String,
    val dictValue: String,
    val dictSort: Int,
    val isDefault: Int,
    val dictDesc: String,
    val dictStatus: Int,
    val createTime: Long,
    val updateTime: Long? = null,
    val remark: String? = null,
    val lotCode: String,
    val changeable: Boolean,
    val extendMark: Int,
)

data class CarBlackInfoData(
    val pageIndex: Int,
    val pageSize: Int,
    val totalCount: Int,
    val carBlackList: List<Map<String, JsonNode>>,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class GetCarCardListRequest(
    val pageIndex: Int = 1,
    val pageSize: Int = 100,
    override val appId: Int,
    override val parkId: String,
    override val ts: Long,
    override val reqId: String,
    override val key: String,
) : BaseRequest(appId, parkId, "getCarCardList", ts, reqId, key)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class AddCarCardRequest(
    val userId: Int,
    val userName: String,
    val cardInfo: String,
    val carLotList: String,
    val plateNoInfo: String,
    override val appId: Int,
    override val parkId: String,
    override val ts: Long,
    override val reqId: String,
    override val key: String,
) : BaseRequest(appId, parkId, "addCarCardNo", ts, reqId, key)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class GetCarCardInfoRequest(
    val cardId: Long? = null,
    val plateNo: String? = null,
    override val appId: Int,
    override val parkId: String,
    override val ts: Long,
    override val reqId: String,
    override val key: String,
) : BaseRequest(appId, parkId, "getCarCardInfo", ts, reqId, key)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ModifyCarCardRequest(
    val userId: Int,
    val userName: String,
    val cardInfo: String,
    val carLotList: String,
    val plateNoInfo: String,
    override val appId: Int,
    override val parkId: String,
    override val ts: Long,
    override val reqId: String,
    override val key: String,
) : BaseRequest(appId, parkId, "modifyCarCardNo", ts, reqId, key)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class DelCarCardRequest(
    val cardId: Long,
    override val appId: Int,
    override val parkId: String,
    override val ts: Long,
    override val reqId: String,
    override val key: String,
) : BaseRequest(appId, parkId, "delCarCardInfo", ts, reqId, key)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class PayCarCardFeeRequest(
    val userId: Int,
    val userName: String,
    val cardId: Long,
    val carType: Int,
    val payChannel: Int,
    val chargeMethod: Int,
    val chargeNumber: Int,
    val amount: Double,
    val freeNumber: Int,
    val validFrom: String,
    val validTo: String,
    val createTime: String,
    override val appId: Int,
    override val parkId: String,
    override val ts: Long,
    override val reqId: String,
    override val key: String,
) : BaseRequest(appId, parkId, "payCarCardFee", ts, reqId, key)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class RefundCarCardFeeRequest(
    val userId: Int,
    val userName: String,
    val cardId: Long,
    val carType: Int,
    val payChannel: Int,
    val refundMethod: Int,
    val refundNumber: Int,
    val amount: Double,
    val freeNumber: Int,
    val validFrom: String,
    val validTo: String,
    val createTime: String,
    val remark: String,
    override val appId: Int,
    override val parkId: String,
    override val ts: Long,
    override val reqId: String,
    override val key: String,
) : BaseRequest(appId, parkId, "refundCarCardFee", ts, reqId, key)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class GetCardInfoByUserRequest(
    val plateNo: String,
    override val appId: Int,
    override val parkId: String,
    override val ts: Long,
    override val reqId: String,
    override val key: String,
) : BaseRequest(appId, parkId, "getCardInfoByUser", ts, reqId, key)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class GetCarInoutInfoRequest(
    val plateNo: String? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val pageIndex: Int = 1,
    val pageSize: Int = 1000,
    override val appId: Int,
    override val parkId: String,
    override val ts: Long,
    override val reqId: String,
    override val key: String,
) : BaseRequest(appId, parkId, "getCarInoutInfo", ts, reqId, key)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class GetParkingPlaceAreaRequest(
    override val appId: Int,
    override val parkId: String,
    override val ts: Long,
    override val reqId: String,
    override val key: String,
) : BaseRequest(appId, parkId, "getParkingPlaceArea", ts, reqId, key)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class GetDictDataListRequest(
    val dictType: String? = null,
    override val appId: Int,
    override val parkId: String,
    override val ts: Long,
    override val reqId: String,
    override val key: String,
) : BaseRequest(appId, parkId, "GetDictDataList", ts, reqId, key)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class QueryCarBlackInfoRequest(
    val plateNo: String? = null,
    val pageIndex: Int = 1,
    val pageSize: Int = 100,
    override val appId: Int,
    override val parkId: String,
    override val ts: Long,
    override val reqId: String,
    override val key: String,
) : BaseRequest(appId, parkId, "queryCarBlackInfo", ts, reqId, key)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class AddCarBlackInfoRequest(
    val plateNo: String,
    val reason: String,
    val remark: String = "",
    override val appId: Int,
    override val parkId: String,
    override val ts: Long,
    override val reqId: String,
    override val key: String,
) : BaseRequest(appId, parkId, "addCarBlackInfo", ts, reqId, key)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ModifyCarBlackInfoRequest(
    val id: Long,
    val plateNo: String,
    val reason: String,
    val remark: String = "",
    override val appId: Int,
    override val parkId: String,
    override val ts: Long,
    override val reqId: String,
    override val key: String,
) : BaseRequest(appId, parkId, "modifyCarBlackInfo", ts, reqId, key)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class DelCarBlackInfoRequest(
    val id: Long? = null,
    val plateNo: String? = null,
    override val appId: Int,
    override val parkId: String,
    override val ts: Long,
    override val reqId: String,
    override val key: String,
) : BaseRequest(appId, parkId, "delCarBlackInfo", ts, reqId, key)
