package top.foxball.cartask.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import top.foxball.cartask.entity.User

interface UserRepository : JpaRepository<User, Long> {
    fun findByUsername(username: String): User?

    fun findByPhone(phone: String): User?

    fun existsByUsername(username: String): Boolean

    fun findAllByUsernameIn(usernames: Collection<String>): List<User>

    fun findAllByRoleIn(roles: Collection<String>): List<User>

    fun existsByUsernameAndIdNot(username: String, id: Long): Boolean

    fun existsByEmail(email: String): Boolean

    fun existsByEmailAndIdNot(email: String, id: Long): Boolean

    fun countByRoleAndEnabledTrueAndStatus(role: String, status: User.Status): Long

    /**
     * 按部门范围分页查询。
     *
     * 范围过滤必须下推到 SQL：用户列表要返回 totalElements，Kotlin 侧事后过滤会让总数失真，
     * 而 Excel 导出是按这个总数循环捞全量的，过滤掉了也发现不了。
     */
    fun findAllByDepartment_IdIn(departmentIds: Collection<Long>, pageable: Pageable): Page<User>
}
