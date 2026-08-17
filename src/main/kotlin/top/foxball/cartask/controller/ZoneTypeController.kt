package top.foxball.cartask.controller

import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import top.foxball.cartask.entity.type.ZoneType
import top.foxball.cartask.service.ZoneTypeService
import top.foxball.cartask.shared.ResponseBuilder

@RestController
@RequestMapping("/api/zone-types")
/** 区域类型字典的管理接口。 */
class ZoneTypeController(service: ZoneTypeService, responseBuilder: ResponseBuilder) : CrudController<ZoneType>(service, responseBuilder)
