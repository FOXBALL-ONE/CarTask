package top.foxball.cartask.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import top.foxball.cartask.entity.StoredFile
import java.util.*

interface StoredFileRepository : JpaRepository<StoredFile, UUID> {
    
    fun findByBusinessTypeAndBusinessId(businessType: String, businessId: String): List<StoredFile>
    fun existsBySha256AndBusinessType(sha256: String, businessType: String): Boolean
    
    
    @Query("select coalesce(sum(f.sizeBytes), 0) from StoredFile f")
    fun sumSizeBytes(): Long
}
