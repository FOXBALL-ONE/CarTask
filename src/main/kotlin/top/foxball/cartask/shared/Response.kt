package top.foxball.cartask.shared

import com.fasterxml.jackson.annotation.JsonInclude

/** 统一响应体：状态码 + 消息 + 数据。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class Response(
    val status: Int,
    val message: String,
    val data: Any?,
) {
    val success: Boolean
        get() = status in 200..299
}
