package top.foxball.cartask.service.impl

import org.springframework.stereotype.Service
import top.foxball.cartask.entity.type.RestrictionType
import top.foxball.cartask.repository.RestrictionTypeRepository
import top.foxball.cartask.service.RestrictionTypeService

@Service
class RestrictionTypeServiceImpl(repository: RestrictionTypeRepository) : JpaCrudService<RestrictionType>(repository), RestrictionTypeService
