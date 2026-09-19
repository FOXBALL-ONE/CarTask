package top.foxball.cartask.controller

import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import top.foxball.cartask.config.AppRestartSignal
import top.foxball.cartask.service.SystemConfigService
import top.foxball.cartask.shared.Response
import top.foxball.cartask.shared.ResponseBuilder

@RestController
@RequestMapping("/api/system/config")
/** class SystemConfigController：系统配置的读取、保存与重载入口。 */
class SystemConfigController(
    private val systemConfigService: SystemConfigService,
    private val appRestartSignal: AppRestartSignal,
    private val responseBuilder: ResponseBuilder,
) {
    @GetMapping
    @PreAuthorize("hasAuthority('system-config:read')")
    /** read：读取当前生效的配置项，密钥字段以掩码返回。 */
    fun read(): ResponseEntity<Response> =
        responseBuilder.ok().data(systemConfigService.read()).build()


    @PutMapping
    @PreAuthorize("hasAuthority('system-config:write')")
    /** update：把配置写回 .env。此时尚未生效，需要再调 reload。 */
    fun update(@RequestBody values: Map<String, String>): ResponseEntity<Response> {
        systemConfigService.write(values)
        return responseBuilder.ok().message("配置已保存").data(systemConfigService.read()).build()
    }


    @PostMapping("/reload")
    @PreAuthorize("hasAuthority('system-config:write')")
    /**
     * reload：按新写入的 .env 重启应用，使配置真正生效。
     *
     * 重启由 main() 完成：这里只发信号，当前响应会先返回给前端，约 0.9 秒后上下文才开始重建。
     */
    fun reload(): ResponseEntity<Response> {
        appRestartSignal.request()
        return responseBuilder.ok().message("正在按新配置重启服务").build()
    }
}
