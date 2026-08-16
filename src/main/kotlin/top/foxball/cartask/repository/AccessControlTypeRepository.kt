package top.foxball.cartask.repository

import org.springframework.data.jpa.repository.JpaRepository
import top.foxball.cartask.entity.type.AccessControlType

interface AccessControlTypeRepository : JpaRepository<AccessControlType, Long>
