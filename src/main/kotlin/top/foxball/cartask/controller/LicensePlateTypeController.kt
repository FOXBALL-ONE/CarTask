package top.foxball.cartask.controller

import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import top.foxball.cartask.entity.type.LicensePlateType
import top.foxball.cartask.service.LicensePlateTypeService
import top.foxball.cartask.shared.ResponseBuilder

@RestController
@RequestMapping("/api/license-plate-types")
class LicensePlateTypeController(service: LicensePlateTypeService, responseBuilder: ResponseBuilder) : CrudController<LicensePlateType>(service, responseBuilder)
