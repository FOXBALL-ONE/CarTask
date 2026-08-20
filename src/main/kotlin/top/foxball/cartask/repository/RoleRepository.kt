package top.foxball.cartask.repository

import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import top.foxball.cartask.entity.Role

interface RoleRepository : JpaRepository<Role, Long> {
    @EntityGraph(attributePaths = ["permissions"])
    fun findByNameIgnoreCase(name: String): Role?
}
