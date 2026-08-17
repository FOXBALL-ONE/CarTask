package top.foxball.cartask.service.impl

import org.springframework.stereotype.Service
import top.foxball.cartask.entity.Position
import top.foxball.cartask.repository.PositionRepository
import top.foxball.cartask.service.PositionService

@Service
class PositionServiceImpl(repository: PositionRepository) : JpaCrudService<Position>(repository), PositionService
