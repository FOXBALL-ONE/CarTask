package top.foxball.cartask.repository

import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import top.foxball.cartask.entity.ViolationRecord

interface ViolationRecordRepository : JpaRepository<ViolationRecord, Long> {
    @EntityGraph(attributePaths = ["subject", "violationType"])
    @Query("select record from ViolationRecord record join fetch record.violationType join fetch record.subject")
    
    
    fun findAllWithViolationType(): List<ViolationRecord>
    
    
    fun existsByViolationTypeId(violationTypeId: Long): Boolean
}
