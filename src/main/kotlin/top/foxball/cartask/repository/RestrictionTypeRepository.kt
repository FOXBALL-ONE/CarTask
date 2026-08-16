package top.foxball.cartask.repository

import org.springframework.data.jpa.repository.JpaRepository
import top.foxball.cartask.entity.type.RestrictionType

interface RestrictionTypeRepository : JpaRepository<RestrictionType, Long>
