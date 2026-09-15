package top.foxball.cartask.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import top.foxball.cartask.entity.AccessControl

interface AccessControlRepository : JpaRepository<AccessControl, Long> {
    /**
     * findByPersonNumber：查询或读取相关数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param personNumber 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    fun findByPersonNumber(personNumber: String): AccessControl?

    /**
     * 按部门范围分页查询。
     *
     * 门禁授权有真正的部门外键，范围过滤可以直接下推到 SQL，不必像门禁人员那样
     * 「全表加载 + 内存过滤 + 内存分页」——那样分页的 total 还会随范围漂移。
     */
    fun findByDepartment_IdIn(departmentIds: Collection<Long>, pageable: Pageable): Page<AccessControl>
}
