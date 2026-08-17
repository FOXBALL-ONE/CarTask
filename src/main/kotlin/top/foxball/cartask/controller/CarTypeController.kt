package top.foxball.cartask.controller

import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import top.foxball.cartask.entity.type.CarType
import top.foxball.cartask.service.CarTypeService
import top.foxball.cartask.shared.ResponseBuilder

@RestController
@RequestMapping("/api/car-types")
class CarTypeController(service: CarTypeService, responseBuilder: ResponseBuilder) : CrudController<CarType>(service, responseBuilder)
