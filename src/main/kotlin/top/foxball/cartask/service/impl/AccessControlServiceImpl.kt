package top.foxball.cartask.service.impl

import org.springframework.stereotype.Service
import top.foxball.cartask.entity.AccessControl
import top.foxball.cartask.repository.AccessControlRepository
import top.foxball.cartask.service.AccessControlService

@Service
class AccessControlServiceImpl(repository: AccessControlRepository) : JpaCrudService<AccessControl>(repository), AccessControlService
