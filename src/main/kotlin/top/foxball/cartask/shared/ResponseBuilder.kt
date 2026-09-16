package top.foxball.cartask.shared

/**
 * ResponseBuilder 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component


@Component
/**
 * ResponseBuilder 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
class ResponseBuilder {
    
    
    /**
     * ok 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun ok(): Builder = Builder(ResponseCode.OK)
    
    
    /**
     * created 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun created(): Builder = Builder(ResponseCode.CREATED)
    
    
    /**
     * notFound 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun notFound(): Builder = Builder(ResponseCode.NOT_FOUND)
    
    
    /**
     * badRequest 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun badRequest(): Builder = Builder(ResponseCode.BAD_REQUEST)
    
    
    /**
     * forbidden 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun forbidden(): Builder = Builder(ResponseCode.FORBIDDEN)
    
    
    /**
     * unauthorized 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun unauthorized(): Builder = Builder(ResponseCode.UNAUTHORIZED)
    
    
    /**
     * tooManyRequests 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun tooManyRequests(): Builder = Builder(ResponseCode.TOO_MANY_REQUESTS)
    
    
    /**
     * exception 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun exception(): Builder = Builder(ResponseCode.INTERNAL_SERVER_ERROR)
    
    
    /**
     * serviceUnavailable 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun serviceUnavailable(): Builder = Builder(ResponseCode.SERVICE_UNAVAILABLE)
    
    
    /**
     * teapot 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun teapot(): Builder = Builder(ResponseCode.IM_A_TEAPOT)
    
    
    /**
     * status 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
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
         * message 函数：执行与该组件职责相关的业务操作。
         * 参数和返回值遵循调用方与领域服务之间的约定。
         */
        fun message(message: String?) = apply {
            this.customMessage = message
        }
        
        
        /**
         * data 函数：执行与该组件职责相关的业务操作。
         * 参数和返回值遵循调用方与领域服务之间的约定。
         */
        fun data(data: Any?) = apply {
            this.data = data
        }
        
        
        /**
         * header 函数：执行与该组件职责相关的业务操作。
         * 参数和返回值遵循调用方与领域服务之间的约定。
         */
        fun header(key: String, value: String) = apply {
            this.headers.add(key, value)
        }
        
        
        /**
         * headers 函数：执行与该组件职责相关的业务操作。
         * 参数和返回值遵循调用方与领域服务之间的约定。
         */
        fun headers(httpHeaders: HttpHeaders) = apply {
            this.headers.addAll(httpHeaders)
        }
        
        
        /**
         * retryAfter 函数：执行与该组件职责相关的业务操作。
         * 参数和返回值遵循调用方与领域服务之间的约定。
         */
        fun retryAfter(seconds: Long) = apply {
            this.headers.add("Retry-After", seconds.toString())
        }
        
        
        /**
         * build 函数：执行与该组件职责相关的业务操作。
         * 参数和返回值遵循调用方与领域服务之间的约定。
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


