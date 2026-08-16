package top.foxball.cartask.repository

import org.springframework.data.jpa.repository.JpaRepository
import top.foxball.cartask.entity.AccessControl

interface AccessControlRepository : JpaRepository<AccessControl, Long>
