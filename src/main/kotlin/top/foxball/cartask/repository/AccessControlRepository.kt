package top.foxball.cartask.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import top.foxball.cartask.entity.AccessControl

interface AccessControlRepository : JpaRepository<AccessControl, Long> {
    
    
    fun findByPersonNumber(personNumber: String): AccessControl?
    
    
    fun findByDepartment_IdIn(departmentIds: Collection<Long>, pageable: Pageable): Page<AccessControl>
}
