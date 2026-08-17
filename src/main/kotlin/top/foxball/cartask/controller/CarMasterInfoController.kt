package top.foxball.cartask.controller

import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import top.foxball.cartask.entity.CarMasterInfo
import top.foxball.cartask.service.CarMasterInfoService
import top.foxball.cartask.shared.ResponseBuilder

@RestController
@RequestMapping("/api/car-master-infos")
/** 车辆主档的管理接口。 */
class CarMasterInfoController(service: CarMasterInfoService, responseBuilder: ResponseBuilder) : CrudController<CarMasterInfo>(service, responseBuilder)
