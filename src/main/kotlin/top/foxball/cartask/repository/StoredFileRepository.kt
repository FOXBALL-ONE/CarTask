package top.foxball.cartask.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import top.foxball.cartask.entity.StoredFile
import java.util.*

interface StoredFileRepository : JpaRepository<StoredFile, UUID> {
    /** 按业务关联取文件；同一业务对象可有多个（例如换过多次人脸照片）。 */
    fun findByBusinessTypeAndBusinessId(businessType: String, businessId: String): List<StoredFile>

    /** 登记在册的附件总字节数，用于备份页在真正导出前展示体量。 */
    @Query("select coalesce(sum(f.sizeBytes), 0) from StoredFile f")
    fun sumSizeBytes(): Long
}
