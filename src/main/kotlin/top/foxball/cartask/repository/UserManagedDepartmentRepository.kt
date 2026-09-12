package top.foxball.cartask.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import top.foxball.cartask.entity.UserManagedDepartment

interface UserManagedDepartmentRepository : JpaRepository<UserManagedDepartment, Long> {
    fun findByUserId(userId: Long): List<UserManagedDepartment>

    fun deleteByUserId(userId: Long)

    fun countByDepartmentId(departmentId: Long): Long

    /** 直接返回部门 ID，避免在事务外触碰懒加载的部门关联。 */
    @Query("select assignment.department.id from UserManagedDepartment assignment where assignment.user.id = :userId")
    fun findDepartmentIds(@Param("userId") userId: Long): List<Long>

    @Query(
        """
        select assignment.department.id from UserManagedDepartment assignment
        where assignment.user.id = :userId and assignment.includeDescendants = true
        """,
    )
    fun findDepartmentIdsWithDescendants(@Param("userId") userId: Long): List<Long>
}
