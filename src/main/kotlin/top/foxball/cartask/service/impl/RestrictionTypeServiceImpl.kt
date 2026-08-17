package top.foxball.cartask.service.impl

import org.springframework.stereotype.Service
import top.foxball.cartask.entity.type.RestrictionType
import top.foxball.cartask.repository.RestrictionTypeRepository
import top.foxball.cartask.service.RestrictionTypeService

@Service
/** 基于 JPA 的限制类型字典服务。 */
class RestrictionTypeServiceImpl(repository: RestrictionTypeRepository) : JpaCrudService<RestrictionType>(repository), RestrictionTypeService
