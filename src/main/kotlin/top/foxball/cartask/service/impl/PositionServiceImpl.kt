package top.foxball.cartask.service.impl

import org.springframework.stereotype.Service
import top.foxball.cartask.entity.Position
import top.foxball.cartask.repository.PositionRepository
import top.foxball.cartask.service.PositionService

@Service
/** 基于 JPA 的职位字典服务。 */
class PositionServiceImpl(repository: PositionRepository) : JpaCrudService<Position>(repository), PositionService
