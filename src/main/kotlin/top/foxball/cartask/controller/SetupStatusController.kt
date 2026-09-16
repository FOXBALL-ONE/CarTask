package top.foxball.cartask.controller

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import top.foxball.cartask.shared.Response
import top.foxball.cartask.shared.ResponseBuilder


@RestController
@RequestMapping("/api/setup")
/** class SetupStatusController：Web 控制器，负责接收 HTTP 请求、调用领域服务并构造统一响应。 */
class SetupStatusController(
    private val responseBuilder: ResponseBuilder,
) {
    @GetMapping("/status")
            
            
            /** status：处理对应的 HTTP 接口请求，完成参数绑定、业务调用和响应封装。 */
    fun status(): ResponseEntity<Response> {
        data class Response(
            @param:JsonProperty("setup_required") val setupRequired: Boolean,
        )
        
        return responseBuilder.ok().data(Response(setupRequired = false)).build()
    }
}
