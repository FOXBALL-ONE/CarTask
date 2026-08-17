package top.foxball.cartask.controller

import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import top.foxball.cartask.entity.AccessRecord
import top.foxball.cartask.service.AccessRecordService
import top.foxball.cartask.shared.ResponseBuilder

@RestController
@RequestMapping("/api/access-records")
/** 车辆进出记录的查询与管理接口。 */
class AccessRecordController(service: AccessRecordService, responseBuilder: ResponseBuilder) : CrudController<AccessRecord>(service, responseBuilder)
