package top.foxball.cartask.repository

import org.springframework.data.jpa.repository.JpaRepository
import top.foxball.cartask.entity.ViolationSubject

interface ViolationSubjectRepository : JpaRepository<ViolationSubject, Long> {
    
    
    fun findBySubjectNumber(subjectNumber: String): ViolationSubject?
}
