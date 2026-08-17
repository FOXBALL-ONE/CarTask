package top.foxball.cartask.controller

import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import top.foxball.cartask.entity.AccessControl
import top.foxball.cartask.service.AccessControlService
import top.foxball.cartask.shared.ResponseBuilder

@RestController
@RequestMapping("/api/access-controls")
class AccessControlController(service: AccessControlService, responseBuilder: ResponseBuilder) : CrudController<AccessControl>(service, responseBuilder)
