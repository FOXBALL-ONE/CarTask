package top.foxball.cartask.service.impl

import org.springframework.stereotype.Service
import top.foxball.cartask.entity.type.LicensePlateType
import top.foxball.cartask.repository.LicensePlateTypeRepository
import top.foxball.cartask.service.LicensePlateTypeService

@Service
/** 基于 JPA 的车牌类型字典服务。 */
class LicensePlateTypeServiceImpl(repository: LicensePlateTypeRepository) : JpaCrudService<LicensePlateType>(repository), LicensePlateTypeService
