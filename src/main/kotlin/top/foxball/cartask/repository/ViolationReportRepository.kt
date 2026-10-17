package top.foxball.cartask.repository

import org.springframework.data.jpa.repository.JpaRepository
import top.foxball.cartask.entity.ViolationReport

interface ViolationReportRepository : JpaRepository<ViolationReport, Long> {
    fun findByReporterUserIdOrderByCreatedAtDesc(reporterUserId: Long): List<ViolationReport>
}
