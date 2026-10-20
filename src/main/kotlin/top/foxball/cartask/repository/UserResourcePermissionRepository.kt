package top.foxball.cartask.repository

import org.springframework.data.jpa.repository.JpaRepository
import top.foxball.cartask.entity.UserResourcePermission

interface UserResourcePermissionRepository : JpaRepository<UserResourcePermission, Long> {
    fun findByUserId(userId: Long): List<UserResourcePermission>

    fun deleteByUserId(userId: Long)
}
