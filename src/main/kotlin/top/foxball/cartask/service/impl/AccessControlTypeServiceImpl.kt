package top.foxball.cartask.service.impl

import org.springframework.stereotype.Service
import top.foxball.cartask.entity.type.AccessControlType
import top.foxball.cartask.repository.AccessControlTypeRepository
import top.foxball.cartask.service.AccessControlTypeService

@Service
class AccessControlTypeServiceImpl(repository: AccessControlTypeRepository) : JpaCrudService<AccessControlType>(repository), AccessControlTypeService
