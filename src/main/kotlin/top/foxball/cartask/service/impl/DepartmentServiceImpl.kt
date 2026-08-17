package top.foxball.cartask.service.impl

import org.springframework.stereotype.Service
import top.foxball.cartask.entity.Department
import top.foxball.cartask.repository.DepartmentRepository
import top.foxball.cartask.service.DepartmentService

@Service
class DepartmentServiceImpl(repository: DepartmentRepository) : JpaCrudService<Department>(repository), DepartmentService
