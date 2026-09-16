package top.foxball.cartask.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import top.foxball.cartask.entity.AccessRecord
import java.time.LocalDateTime

interface AccessRecordRepository : JpaRepository<AccessRecord, Long>, JpaSpecificationExecutor<AccessRecord> {
    
    
    fun findTopByOrderByInAndOutTimeDescIdDesc(): AccessRecord?
    
    
    @Query(
        """
        select distinct record.carNumber from AccessRecord record
        where record.inAndOutTime >= :startTime
          and record.carNumber is not null
          and record.carNumber <> ''
        """,
    )
    
    
    fun findDistinctCarNumbersSince(@Param("startTime") startTime: LocalDateTime): List<String>
    
    
    fun findBySourceRecordId(sourceRecordId: String): AccessRecord?
    
    
    fun findTop100ByPhotoSyncStatusOrderByIdAsc(
        photoSyncStatus: AccessRecord.PhotoSyncStatus,
    ): List<AccessRecord>
    
    
    fun countByPhotoSyncStatus(photoSyncStatus: AccessRecord.PhotoSyncStatus): Long
    
    
    @Query("select count(record) from AccessRecord record where record.carNumberNormalized is null")
    fun countByCarNumberNormalizedIsNull(): Long
    
    @Query(
        """
        select record from AccessRecord record
        where record.carNumber = :carNumber
          and record.inAndOut = :inAndOut
        order by record.inAndOutTime desc, record.id desc
        """,
    )
    
    
    fun findFirstByCarNumberAndInAndOutOrderByInAndOutTimeDesc(
        @Param("carNumber") carNumber: String,
        @Param("inAndOut") inAndOut: AccessRecord.InAndOut,
    ): AccessRecord?
    
    
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
