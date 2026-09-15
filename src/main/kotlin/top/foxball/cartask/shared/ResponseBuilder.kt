package top.foxball.cartask.shared

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component

/** 构造统一 [ResponseEntity]/[Response] 的建造者，封装常见 HTTP 状态及分页/重试头。 */
@Component
class ResponseBuilder {

    /**
     * ok：执行当前模块中的业务操作。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun ok(): Builder = Builder(ResponseCode.OK)

    /**
     * created：创建、保存或初始化相关数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun created(): Builder = Builder(ResponseCode.CREATED)

    /**
     * notFound：执行当前模块中的业务操作。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun notFound(): Builder = Builder(ResponseCode.NOT_FOUND)

    /**
     * badRequest：执行当前模块中的业务操作。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun badRequest(): Builder = Builder(ResponseCode.BAD_REQUEST)

    /**
     * forbidden：执行当前模块中的业务操作。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun forbidden(): Builder = Builder(ResponseCode.FORBIDDEN)

    /**
     * unauthorized：执行当前模块中的业务操作。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun unauthorized(): Builder = Builder(ResponseCode.UNAUTHORIZED)

    /**
     * tooManyRequests：转换、构建或格式化数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun tooManyRequests(): Builder = Builder(ResponseCode.TOO_MANY_REQUESTS)

    /**
     * exception：执行当前模块中的业务操作。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun exception(): Builder = Builder(ResponseCode.INTERNAL_SERVER_ERROR)

    /**
     * serviceUnavailable：执行当前模块中的业务操作。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun serviceUnavailable(): Builder = Builder(ResponseCode.SERVICE_UNAVAILABLE)

    /**
     * teapot：执行当前模块中的业务操作。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun teapot(): Builder = Builder(ResponseCode.IM_A_TEAPOT)

    /**
     * status：执行当前模块中的业务操作。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param status 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun status(status: HttpStatus): Builder = Builder(status)

    inner class Builder {
        private var status: Int

        private var defaultMessage: String

        private var customMessage: String? = null

        private var data: Any? = null

        private val headers = HttpHeaders()

        constructor(responseCode: ResponseCode) {
            this.status = responseCode.code
            this.defaultMessage = responseCode.message
        }

        constructor(httpStatus: HttpStatus) {
            this.status = httpStatus.value()
            this.defaultMessage = httpStatus.reasonPhrase
        }

        /**
         * message：执行当前模块中的业务操作。
         *
         * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
         * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
         * @param message 参与本次处理的输入参数。
         * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
         */
        fun message(message: String?) = apply {
            this.customMessage = message
        }

        /**
         * data：执行当前模块中的业务操作。
         *
         * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
         * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
         * @param data 参与本次处理的输入参数。
         * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
         */
        fun data(data: Any?) = apply {
            this.data = data
        }

        /**
         * header：执行当前模块中的业务操作。
         *
         * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
         * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
         * @param key 参与本次处理的输入参数。
         * @param value 参与本次处理的输入参数。
         * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
         */
        fun header(key: String, value: String) = apply {
            this.headers.add(key, value)
        }

        /**
         * headers：执行当前模块中的业务操作。
         *
         * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
         * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
         * @param httpHeaders 参与本次处理的输入参数。
         * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
         */
        fun headers(httpHeaders: HttpHeaders) = apply {
            this.headers.addAll(httpHeaders)
        }

        /**
         * retryAfter：执行当前模块中的业务操作。
         *
         * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
         * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
         * @param seconds 参与本次处理的输入参数。
         * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
         */
        fun retryAfter(seconds: Long) = apply {
            this.headers.add("Retry-After", seconds.toString())
        }

        /**
         * build：转换、构建或格式化数据。
         *
         * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
         * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
         * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
         */
        fun build(): ResponseEntity<Response> {
            val finalData = this.data ?: if (this.status in 200..299) HashMap<String, Any?>() else null

            val responseBody = Response(
                status = this.status,
                message = this.customMessage ?: this.defaultMessage,
                data = finalData
            )

            return ResponseEntity
                .status(this.status)
                .headers(this.headers)
                .body(responseBody)
        }
    }

    enum class ResponseCode(val code: Int, val message: String) {
        OK(200, "操作成功"),
        CREATED(201, "操作成功"),
        NOT_FOUND(404, "Not Found"),
        UNAUTHORIZED(401, "Unauthorized"),
        FORBIDDEN(403, "Forbidden"),
        BAD_REQUEST(400, "Bad Request"),
        TOO_MANY_REQUESTS(429, "Too Many Requests"),
        INTERNAL_SERVER_ERROR(500, "Internal Server Error"),
        IM_A_TEAPOT(418, "I'm a teapot"),
        SERVICE_UNAVAILABLE(503, "Service Unavailable"),
    }
}
