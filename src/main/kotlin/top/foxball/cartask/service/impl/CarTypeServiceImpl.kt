package top.foxball.cartask.service.impl

import org.springframework.stereotype.Service
import top.foxball.cartask.entity.type.CarType
import top.foxball.cartask.repository.CarTypeRepository
import top.foxball.cartask.service.CarTypeService

@Service
class CarTypeServiceImpl(repository: CarTypeRepository) : JpaCrudService<CarType>(repository), CarTypeService
