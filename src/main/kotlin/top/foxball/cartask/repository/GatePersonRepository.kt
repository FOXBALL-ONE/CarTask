package top.foxball.cartask.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import jakarta.persistence.LockModeType
import top.foxball.cartask.entity.GatePerson

interface GatePersonRepository : JpaRepository<GatePerson, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from GatePerson p where p.id = :id")
    fun findLockedById(id: Long): GatePerson?
    
    
    fun findByCode(code: String): GatePerson?
    
    
    fun findByPhone(phone: String): List<GatePerson>
    
    
    fun findByDepartmentCode(departmentCode: String): List<GatePerson>
    
    
    fun existsByCode(code: String): Boolean
    
    
    fun existsByCodeAndIdNot(code: String, id: Long): Boolean
    
    
    fun existsByIdCard(idCard: String): Boolean
    
    
    fun existsByIdCardAndIdNot(idCard: String, id: Long): Boolean
    
    
    fun countByDepartmentCode(departmentCode: String): Long
}
