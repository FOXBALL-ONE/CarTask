package top.foxball.cartask.service

import org.springframework.web.multipart.MultipartFile
import top.foxball.cartask.entity.GatePerson
import java.nio.file.Path
import java.time.LocalDateTime

/** 统一的人脸登记业务入口。HTTP 层不直接拼接单位、文件和审核状态。 */
interface FaceRegistrationService {
    data class ListQuery(
        val keyword: String? = null,
        val departmentCode: String? = null,
        val approveStatus: GatePerson.ApproveStatus? = null,
        val syncStatus: GatePerson.SyncStatus? = null,
        /** 旧门禁人员接口按展示名称筛选，新的登记接口按编码筛选。 */
        val departmentName: String? = null,
    )

    data class PageData(val items: List<Detail>, val total: Int)

    data class Detail(
        val id: Long,
        val code: String,
        val name: String,
        val phone: String,
        val idCard: String,
        val dept: String,
        val departmentCode: String,
        val photoUrl: String?,
        val approveStatus: GatePerson.ApproveStatus,
        val syncStatus: GatePerson.SyncStatus,
        val createTime: LocalDateTime,
        val updatedAt: LocalDateTime,
    )

    data class DownloadData(
        val path: Path,
        val originalFilename: String,
        val contentType: String?,
        val sizeBytes: Long,
    )

    data class BatchReviewResult(val reviewed: Int, val failures: List<BatchFailure> = emptyList())
    data class BatchFailure(val id: Long, val reason: String)

    fun list(query: ListQuery, page: Int, pageSize: Int): PageData
    fun get(id: Long): Detail
    fun photo(id: Long): DownloadData
    fun register(
        name: String,
        phone: String,
        idCard: String,
        photo: MultipartFile,
        departmentCode: String? = null,
    ): Detail
    fun registerLegacy(code: String, department: String, name: String, phone: String, idCard: String, photo: MultipartFile): Detail
    fun update(
        id: Long,
        name: String?,
        phone: String?,
        idCard: String?,
        photo: MultipartFile?,
    ): Detail
    fun updateLegacy(
        id: Long,
        code: String?,
        department: String?,
        name: String?,
        phone: String?,
        idCard: String?,
        photo: MultipartFile?,
    ): Detail
    fun review(id: Long, approved: Boolean, reason: String?): Detail
    fun reviewBatch(ids: List<Long>, approved: Boolean, reason: String?): BatchReviewResult
}
