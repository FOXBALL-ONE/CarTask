package top.foxball.cartask.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import top.foxball.cartask.entity.StoredFile
import java.util.UUID

interface StoredFileRepository : JpaRepository<StoredFile, UUID> {
    /** 登记在册的附件总字节数，用于备份页在真正导出前展示体量。 */
    @Query("select coalesce(sum(f.sizeBytes), 0) from StoredFile f")
    fun sumSizeBytes(): Long
}
