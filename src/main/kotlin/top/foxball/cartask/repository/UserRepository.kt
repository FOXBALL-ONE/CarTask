package top.foxball.cartask.repository

import org.springframework.data.jpa.repository.JpaRepository
import top.foxball.cartask.entity.User

interface UserRepository : JpaRepository<User, Long> {
    fun findByUsername(username: String): User?

    fun existsByUsername(username: String): Boolean

    fun findAllByUsernameIn(usernames: Collection<String>): List<User>

    fun findAllByRoleIn(roles: Collection<String>): List<User>

    fun existsByUsernameAndIdNot(username: String, id: Long): Boolean

    fun existsByEmail(email: String): Boolean

    fun existsByEmailAndIdNot(email: String, id: Long): Boolean

    fun countByRoleAndEnabledTrueAndStatus(role: String, status: User.Status): Long
}
