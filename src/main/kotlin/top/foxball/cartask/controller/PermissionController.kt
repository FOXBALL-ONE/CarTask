package top.foxball.cartask.controller

import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import top.foxball.cartask.entity.Permission
import top.foxball.cartask.service.PermissionService
import top.foxball.cartask.shared.ResponseBuilder

@RestController
@RequestMapping("/api/permissions")
class PermissionController(service: PermissionService, responseBuilder: ResponseBuilder) : CrudController<Permission>(service, responseBuilder)
