package top.foxball.cartask.keytop

import com.fasterxml.jackson.annotation.JsonInclude
import tools.jackson.databind.JsonNode
import java.time.LocalDateTime

/** 科拓接口的统一响应。data 保留平台返回的原始 JSON，便于兼容不同车场的数据结构。 */
data class KeytopResponse(
    val code: Int?,
    val message: String?,
    val data: JsonNode?,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class KeytopCardInfo(
    val cardName: String,
    val useName: String,
    val tel: String,
    val roomId: String,
    /** 平台字段原名为 remak。 */
    val remak: String = "",
    val contact: String = "",
    val assist: String = "",
    val cardId: Long? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class KeytopCarLot(
    val lotName: String,
    val carType: Int = 1,
    val sequence: Int = 1,
    val areaName: String,
    val areaId: List<Int> = emptyList(),
    val lotCount: Int = 1,
    val id: Long? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class KeytopPlateNo(
    val plateNo: String,
    val etcNo: String = "",
    val remark: String = "",
    val id: Long? = null,
    val plateState: Int? = null,
)

data class KeytopPayCarCardFeeRequest(
    val userId: Long,
    val userName: String,
    val cardId: Long,
    val carType: Int,
    val validFrom: LocalDateTime,
    val validTo: LocalDateTime,
    val createTime: LocalDateTime = LocalDateTime.now(),
    val orderNo: String = java.util.UUID.randomUUID().toString(),
    val payChannel: Int = 1,
    val chargeMethod: Int = 1,
    val chargeNumber: Int = 1,
    val amount: Int = 0,
    val freeNumber: Int = 0,
)

data class KeytopRefundCarCardFeeRequest(
    val userId: Long,
    val userName: String,
    val cardId: Long,
    val carType: Int,
    val validFrom: LocalDateTime,
    val validTo: LocalDateTime,
    val createTime: LocalDateTime = LocalDateTime.now(),
    val orderNo: String = java.util.UUID.randomUUID().toString(),
    val payChannel: Int = 1,
    val refundMethod: Int = 2,
    val amount: Int = 0,
    val freeNumber: Int = 0,
    val remark: String = "有效期缩短",
)
