package top.foxball.cartask.service.impl

import org.springframework.stereotype.Service
import top.foxball.cartask.entity.type.ReleaseType
import top.foxball.cartask.repository.ReleaseTypeRepository
import top.foxball.cartask.service.ReleaseTypeService

@Service
class ReleaseTypeServiceImpl(repository: ReleaseTypeRepository) : JpaCrudService<ReleaseType>(repository), ReleaseTypeService
