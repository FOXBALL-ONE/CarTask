package top.foxball.cartask.controller

import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import top.foxball.cartask.entity.Position
import top.foxball.cartask.service.PositionService
import top.foxball.cartask.shared.ResponseBuilder

@RestController
@RequestMapping("/api/positions")
/** 职位字典的管理接口。 */
class PositionController(service: PositionService, responseBuilder: ResponseBuilder) : CrudController<Position>(service, responseBuilder)
