package top.foxball.cartask.controller

import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import top.foxball.cartask.entity.Department
import top.foxball.cartask.service.DepartmentService
import top.foxball.cartask.shared.ResponseBuilder

@RestController
@RequestMapping("/api/departments")
/** 组织部门的管理接口。 */
class DepartmentController(service: DepartmentService, responseBuilder: ResponseBuilder) : CrudController<Department>(service, responseBuilder)
