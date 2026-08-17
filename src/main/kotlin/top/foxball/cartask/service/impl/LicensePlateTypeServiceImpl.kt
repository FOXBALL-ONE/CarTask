package top.foxball.cartask.service.impl

import org.springframework.stereotype.Service
import top.foxball.cartask.entity.type.LicensePlateType
import top.foxball.cartask.repository.LicensePlateTypeRepository
import top.foxball.cartask.service.LicensePlateTypeService

@Service
class LicensePlateTypeServiceImpl(repository: LicensePlateTypeRepository) : JpaCrudService<LicensePlateType>(repository), LicensePlateTypeService
