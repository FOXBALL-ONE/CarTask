package top.foxball.cartask.controller

import org.springframework.http.CacheControl
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import top.foxball.cartask.service.SystemBrandingService
import top.foxball.cartask.shared.Response
import top.foxball.cartask.shared.ResponseBuilder

data class BrandingUpdateRequest(
    val title: String? = null,
    val logo: String? = null,
)


@RestController
/** class SystemBrandingController：侧边栏与登录页的图标、标题。读取匿名开放，修改需要系统配置写权限。 */
class SystemBrandingController(
    private val systemBrandingService: SystemBrandingService,
    private val responseBuilder: ResponseBuilder,
) {
    /** branding：登录页在会话之外调用，SecurityConfig 中已放行匿名访问。 */
    @GetMapping("/api/branding")
    fun branding(): ResponseEntity<Response> =
        responseBuilder.ok()
            .header(HttpHeaders.CACHE_CONTROL, CacheControl.noCache().headerValue.orEmpty())
            .data(systemBrandingService.read())
            .build()
    
    
    @PutMapping("/api/system/branding")
    @PreAuthorize("hasAuthority('system-config:write')")
    fun update(@RequestBody request: BrandingUpdateRequest): ResponseEntity<Response> =
        responseBuilder.ok().message("系统标识已保存")
            .data(systemBrandingService.update(request.title, request.logo))
            .build()
}
