package top.foxball.cartask.controller

import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import top.foxball.cartask.entity.type.AccessControlType
import top.foxball.cartask.service.AccessControlTypeService
import top.foxball.cartask.shared.ResponseBuilder

@RestController
@RequestMapping("/api/access-control-types")
/** 门禁授权类型字典的管理接口。 */
class AccessControlTypeController(service: AccessControlTypeService, responseBuilder: ResponseBuilder) : CrudController<AccessControlType>(service, responseBuilder)
