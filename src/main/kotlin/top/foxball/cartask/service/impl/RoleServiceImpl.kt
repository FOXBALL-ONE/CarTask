package top.foxball.cartask.service.impl

import org.springframework.stereotype.Service
import top.foxball.cartask.entity.Role
import top.foxball.cartask.repository.RoleRepository
import top.foxball.cartask.service.RoleService

@Service
class RoleServiceImpl(repository: RoleRepository) : JpaCrudService<Role>(repository), RoleService
