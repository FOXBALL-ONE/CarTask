package top.foxball.cartask.service.impl

import org.springframework.stereotype.Service
import top.foxball.cartask.entity.Permission
import top.foxball.cartask.repository.PermissionRepository
import top.foxball.cartask.service.PermissionService

@Service
/** 基于 JPA 的权限字典服务。 */
class PermissionServiceImpl(repository: PermissionRepository) : JpaCrudService<Permission>(repository), PermissionService
