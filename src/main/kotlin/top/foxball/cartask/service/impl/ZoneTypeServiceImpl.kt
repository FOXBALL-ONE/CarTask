package top.foxball.cartask.service.impl

import org.springframework.stereotype.Service
import top.foxball.cartask.entity.type.ZoneType
import top.foxball.cartask.repository.ZoneTypeRepository
import top.foxball.cartask.service.ZoneTypeService

@Service
/** 基于 JPA 的区域类型字典服务。 */
class ZoneTypeServiceImpl(repository: ZoneTypeRepository) : JpaCrudService<ZoneType>(repository), ZoneTypeService
