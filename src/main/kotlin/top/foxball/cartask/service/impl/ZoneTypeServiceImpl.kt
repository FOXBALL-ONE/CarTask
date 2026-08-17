package top.foxball.cartask.service.impl

import org.springframework.stereotype.Service
import top.foxball.cartask.entity.type.ZoneType
import top.foxball.cartask.repository.ZoneTypeRepository
import top.foxball.cartask.service.ZoneTypeService

@Service
class ZoneTypeServiceImpl(repository: ZoneTypeRepository) : JpaCrudService<ZoneType>(repository), ZoneTypeService
