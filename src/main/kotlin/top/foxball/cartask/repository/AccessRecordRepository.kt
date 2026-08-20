package top.foxball.cartask.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import top.foxball.cartask.entity.AccessRecord
import java.time.LocalDateTime

interface AccessRecordRepository : JpaRepository<AccessRecord, Long> {
    fun findTopByOrderByInAndOutTimeDescIdDesc(): AccessRecord?

    @Query(
        """
        select record from AccessRecord record
        where ((:carNumber is null and record.carNumber is null) or record.carNumber = :carNumber)
          and record.inAndOut = :inAndOut
          and record.inAndOutTime = :inAndOutTime
        """,
    )
    fun findByIdentity(
        @Param("carNumber") carNumber: String?,
        @Param("inAndOut") inAndOut: AccessRecord.InAndOut,
        @Param("inAndOutTime") inAndOutTime: LocalDateTime,
    ): AccessRecord?
}
