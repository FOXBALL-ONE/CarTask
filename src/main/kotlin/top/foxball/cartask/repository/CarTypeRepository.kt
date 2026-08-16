package top.foxball.cartask.repository

import org.springframework.data.jpa.repository.JpaRepository
import top.foxball.cartask.entity.type.CarType

interface CarTypeRepository : JpaRepository<CarType, Long>
