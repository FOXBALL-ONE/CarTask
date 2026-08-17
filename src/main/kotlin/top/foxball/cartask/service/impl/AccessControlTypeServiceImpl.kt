package top.foxball.cartask.service.impl

import org.springframework.stereotype.Service
import top.foxball.cartask.entity.type.AccessControlType
import top.foxball.cartask.repository.AccessControlTypeRepository
import top.foxball.cartask.service.AccessControlTypeService

@Service
/** 基于 JPA 的门禁授权类型服务。 */
class AccessControlTypeServiceImpl(repository: AccessControlTypeRepository) : JpaCrudService<AccessControlType>(repository), AccessControlTypeService
