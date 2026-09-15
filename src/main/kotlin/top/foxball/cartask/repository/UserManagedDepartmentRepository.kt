package top.foxball.cartask.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import top.foxball.cartask.entity.UserManagedDepartment

interface UserManagedDepartmentRepository : JpaRepository<UserManagedDepartment, Long> {
    /**
     * findByUserId：查询或读取相关数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param userId 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun findByUserId(userId: Long): List<UserManagedDepartment>

    /**
     * deleteByUserId：删除、清理或撤销相关数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param userId 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun deleteByUserId(userId: Long)

    /**
     * countByDepartmentId：查询或读取相关数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param departmentId 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
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
            /**
             * findDepartmentIdsWithDescendants：查询或读取相关数据。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @param userId 参与本次处理的输入参数。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun findDepartmentIdsWithDescendants(@Param("userId") userId: Long): List<Long>
}
