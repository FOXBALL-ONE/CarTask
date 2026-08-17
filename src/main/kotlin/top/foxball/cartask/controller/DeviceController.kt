package top.foxball.cartask.controller

import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import top.foxball.cartask.entity.Device
import top.foxball.cartask.service.DeviceService
import top.foxball.cartask.shared.ResponseBuilder

@RestController
@RequestMapping("/api/devices")
/** 接入设备的管理接口。 */
class DeviceController(service: DeviceService, responseBuilder: ResponseBuilder) : CrudController<Device>(service, responseBuilder)
