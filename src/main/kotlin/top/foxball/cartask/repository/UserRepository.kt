package top.foxball.cartask.repository

import org.springframework.data.jpa.repository.JpaRepository
import top.foxball.cartask.entity.User

interface UserRepository : JpaRepository<User, Long> {
    fun existsByUsername(username: String): Boolean

    fun existsByEmail(email: String): Boolean
}
