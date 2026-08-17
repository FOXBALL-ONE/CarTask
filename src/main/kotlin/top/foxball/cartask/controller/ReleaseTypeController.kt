package top.foxball.cartask.controller

import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import top.foxball.cartask.entity.type.ReleaseType
import top.foxball.cartask.service.ReleaseTypeService
import top.foxball.cartask.shared.ResponseBuilder

@RestController
@RequestMapping("/api/release-types")
/** 放行类型字典的管理接口。 */
class ReleaseTypeController(service: ReleaseTypeService, responseBuilder: ResponseBuilder) : CrudController<ReleaseType>(service, responseBuilder)
