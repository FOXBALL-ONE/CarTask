package top.foxball.cartask.service.impl

import org.springframework.stereotype.Service
import top.foxball.cartask.entity.Department
import top.foxball.cartask.repository.DepartmentRepository
import top.foxball.cartask.service.DepartmentService

@Service
/** 基于 JPA 的组织部门服务。 */
class DepartmentServiceImpl(repository: DepartmentRepository) : JpaCrudService<Department>(repository), DepartmentService
