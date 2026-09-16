package top.foxball.cartask.shared

/**
 * Response 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */

import com.fasterxml.jackson.annotation.JsonInclude


@JsonInclude(JsonInclude.Include.NON_NULL)
data class Response(
    val status: Int,
    val message: String,
    val data: Any?,
) {
    val success: Boolean
        get() = status in 200..299
}


