package top.foxball.cartask.controller

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import top.foxball.cartask.shared.Response
import top.foxball.cartask.shared.ResponseBuilder

/**
 * 「要不要做配置引导」的询问接口，正常运行模式下固定回答「不用」。
 *
 * 这个路径在两套应用里都存在，是前端的唯一判据：配置模式下由
 * [top.foxball.setup.SetupController] 回答 true，正常模式下由这里回答 false，
 * 前端据此决定跳引导页还是留在登录页。少了正常模式这一半，前端在服务已经配好时会收到 404，
 * 只能把「后端没起来」和「已经配好了」当成同一件事处理。
 *
 * 只此一个接口，且必须免鉴权：它要在拿到 token 之前就能回答。除此之外引导相关的任何接口
 * 都不存在于主应用——配置一旦完成，系统里就不该再有一个「没有账号也能改配置」的入口。
 */
@RestController
@RequestMapping("/api/setup")
class SetupStatusController(
    private val responseBuilder: ResponseBuilder,
) {
    @GetMapping("/status")
            /**
             * status：执行当前模块中的业务操作。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun status(): ResponseEntity<Response> {
        data class Response(
            @param:JsonProperty("setup_required") val setupRequired: Boolean,
        )

        return responseBuilder.ok().data(Response(setupRequired = false)).build()
    }
}
