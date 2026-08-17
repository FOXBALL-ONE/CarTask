package top.foxball.cartask.service.impl

import org.springframework.stereotype.Service
import top.foxball.cartask.entity.AccessControl
import top.foxball.cartask.repository.AccessControlRepository
import top.foxball.cartask.service.AccessControlService

@Service
/** 基于 JPA 的门禁授权记录服务。 */
class AccessControlServiceImpl(repository: AccessControlRepository) : JpaCrudService<AccessControl>(repository), AccessControlService
