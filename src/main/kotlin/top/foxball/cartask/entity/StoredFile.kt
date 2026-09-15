package top.foxball.cartask.entity

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.*

@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(
    name = "stored_files",
    indexes = [
        // 业务对象重挂载与解绑按这一对定位文件，见 FileServiceImpl.linkBusiness/relinkBusiness/unlinkBusiness。
        Index(name = "idx_stored_files_business", columnList = "business_type,business_id"),
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uk_stored_files_stored_filename", columnNames = ["stored_filename"]),
        UniqueConstraint(name = "uk_stored_files_relative_path", columnNames = ["relative_path"]),
    ],
)
class StoredFile {
    @Id
    lateinit var id: UUID

    @Column(name = "original_filename", nullable = false, length = 255)
    lateinit var originalFilename: String

    @Column(name = "stored_filename", nullable = false, unique = true, length = 64)
    lateinit var storedFilename: String

    @Column(name = "relative_path", nullable = false, unique = true, length = 512)
    lateinit var relativePath: String

    @Column(name = "content_type", length = 255)
    var contentType: String? = null

    @Column(name = "size_bytes", nullable = false)
    var sizeBytes: Long = 0

    @Column(nullable = false, length = 64)
    lateinit var sha256: String

    @Column(name = "created_at", nullable = false, updatable = false)
    lateinit var createdAt: LocalDateTime

    /** 上传者账号。系统同步任务从外部平台拉取的抓拍图片没有上传者，为 null。 */
    @Column(name = "uploaded_by_user_id")
    var uploadedByUserId: Long? = null

    /**
     * 归属部门编码，取值来自 [Department.departmentNumber]。
     *
     * 本表此前**没有任何归属字段**，导致只要知道 UUID 就能下载任意附件（含进出抓拍图片）。
     * 数据范围过滤依赖这个字段。
     */
    @Column(name = "department_code", length = 64)
    var departmentCode: String? = null

    /** 业务对象类型，取值见 [BUSINESS_VEHICLE_PLATE] 与 [BUSINESS_GATE_PERSON]。 */
    @Column(name = "business_type", length = 32)
    var businessType: String? = null

    /** 业务对象标识：车辆图片存归一化车牌，门禁图片存人员编号。 */
    @Column(name = "business_id", length = 128)
    var businessId: String? = null

    companion object {
        /** 业务对象：车辆进出抓拍图片，business_id 为归一化车牌。 */
        const val BUSINESS_VEHICLE_PLATE = "vehicle_plate"

        /** 业务对象：门禁人员图片，business_id 为人员编号。 */
        const val BUSINESS_GATE_PERSON = "gate_person"
    }
}
