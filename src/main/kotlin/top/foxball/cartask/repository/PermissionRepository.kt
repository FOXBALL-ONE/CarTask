package top.foxball.cartask.repository

import org.springframework.data.jpa.repository.JpaRepository
import top.foxball.cartask.entity.Permission

interface PermissionRepository : JpaRepository<Permission, Long>
