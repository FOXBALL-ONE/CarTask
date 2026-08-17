package top.foxball.cartask.controller

import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import top.foxball.cartask.entity.Role
import top.foxball.cartask.service.RoleService
import top.foxball.cartask.shared.ResponseBuilder

@RestController
@RequestMapping("/api/roles")
class RoleController(service: RoleService, responseBuilder: ResponseBuilder) : CrudController<Role>(service, responseBuilder)
