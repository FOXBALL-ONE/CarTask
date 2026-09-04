package top.foxball.cartask.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.repository.Query
import top.foxball.cartask.entity.Department

interface DepartmentRepository : JpaRepository<Department, Long> {
    fun findByDepartmentNumber(departmentNumber: String): Department?

    fun existsBySuperiorId(superiorId: Long): Boolean

    @EntityGraph(attributePaths = ["superior"])
    @Query("select department from Department department")
    fun findAllWithSuperior(sort: Sort): List<Department>
}
