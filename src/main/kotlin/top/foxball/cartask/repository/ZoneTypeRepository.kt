package top.foxball.cartask.repository

import org.springframework.data.jpa.repository.JpaRepository
import top.foxball.cartask.entity.type.ZoneType

interface ZoneTypeRepository : JpaRepository<ZoneType, Long>
