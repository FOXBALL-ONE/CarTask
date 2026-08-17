package top.foxball.cartask.controller

import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import top.foxball.cartask.entity.type.RestrictionType
import top.foxball.cartask.service.RestrictionTypeService
import top.foxball.cartask.shared.ResponseBuilder

@RestController
@RequestMapping("/api/restriction-types")
/** 限制类型字典的管理接口。 */
class RestrictionTypeController(service: RestrictionTypeService, responseBuilder: ResponseBuilder) : CrudController<RestrictionType>(service, responseBuilder)
