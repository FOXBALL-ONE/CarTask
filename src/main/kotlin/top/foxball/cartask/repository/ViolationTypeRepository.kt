package top.foxball.cartask.repository

import org.springframework.data.jpa.repository.JpaRepository
import top.foxball.cartask.entity.type.ViolationType

interface ViolationTypeRepository : JpaRepository<ViolationType, Long> {
    fun findFirstByViolationName(violationName: String): ViolationType?
}
