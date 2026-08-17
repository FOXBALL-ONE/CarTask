package top.foxball.cartask.service.impl

import org.springframework.stereotype.Service
import top.foxball.cartask.entity.Role
import top.foxball.cartask.repository.RoleRepository
import top.foxball.cartask.service.RoleService

@Service
/** 基于 JPA 的角色与权限集合服务。 */
class RoleServiceImpl(repository: RoleRepository) : JpaCrudService<Role>(repository), RoleService
