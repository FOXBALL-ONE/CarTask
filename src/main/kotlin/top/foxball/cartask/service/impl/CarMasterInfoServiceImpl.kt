package top.foxball.cartask.service.impl

import org.springframework.stereotype.Service
import top.foxball.cartask.entity.CarMasterInfo
import top.foxball.cartask.repository.CarMasterInfoRepository
import top.foxball.cartask.service.CarMasterInfoService

@Service
class CarMasterInfoServiceImpl(repository: CarMasterInfoRepository) : JpaCrudService<CarMasterInfo>(repository), CarMasterInfoService
