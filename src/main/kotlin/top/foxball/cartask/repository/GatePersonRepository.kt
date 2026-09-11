package top.foxball.cartask.repository

import org.springframework.data.jpa.repository.JpaRepository
import top.foxball.cartask.entity.GatePerson

interface GatePersonRepository : JpaRepository<GatePerson, Long> {
    fun findByCode(code: String): GatePerson?
    fun existsByCode(code: String): Boolean
    fun existsByCodeAndIdNot(code: String, id: Long): Boolean
    fun existsByIdCard(idCard: String): Boolean
    fun existsByIdCardAndIdNot(idCard: String, id: Long): Boolean
}
