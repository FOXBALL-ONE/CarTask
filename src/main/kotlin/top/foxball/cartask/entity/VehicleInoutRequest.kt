package top.foxball.cartask.entity

import com.fasterxml.jackson.annotation.JsonValue
import jakarta.persistence.*
import top.foxball.cartask.scope.DepartmentScoped
import top.foxball.cartask.shared.PlateNumbers
import java.time.LocalDateTime

/**
 * 车辆进出申请登记单：为某个车牌申请下发科拓月卡。
 *
 * 审批通过只写本地状态，科拓下发是审批之后独立的一次外部调用（见 [syncStatus]）：
 * 把外部调用和审批结论放进同一个事务，会因为一次网络超时而丢掉一条已经做出的审批决定，
 * 而审批是不可撤销的业务留痕。下发失败保留 [syncMessage] 并允许页面上手工重试。
 */
@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(
    name = "vehicle_inout_request",
    indexes = [
        // 列表默认按申请时间倒序，同时覆盖按时间范围筛选。
        Index(name = "idx_vehicle_inout_request_apply_time", columnList = "apply_time,id"),
        // 审核状态与下发状态是两个独立维度，各自配合时间排序使用。
        Index(name = "idx_vehicle_inout_request_status", columnList = "status,apply_time"),
        Index(name = "idx_vehicle_inout_request_sync_status", columnList = "sync_status,id"),
        // 同一车牌的未决申请判定按归一化车牌做先查后写（见 plateNormalized）。
        Index(name = "idx_vehicle_inout_request_plate_normalized", columnList = "plate_normalized"),
    ],
)
class VehicleInoutRequest : DepartmentScoped {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    /** 申请的车牌号，写入时按 [top.foxball.cartask.shared.PlateNumbers] 归一化后再存。 */
    @Column(nullable = false, length = 32)
    lateinit var plate: String

    /**
     * 归一化后的车牌，由 [syncPlateNormalized] 在写入时自动维护。
     *
     * 车牌档案是人工维护的，「京A·12345」与「京A12345」是同一辆车。同一车牌只能有一条未决申请的
     * 判断必须落在归一化形式上，否则换个写法就能为同一辆车登记第二条、最终在科拓侧多出一张月卡。
     * 用生命周期回调而不是在调用点赋值，是为了不依赖调用方记得——漏一处就是一次重复发卡。
     */
    @Column(name = "plate_normalized", length = 32)
    var plateNormalized: String? = null

    /** 车牌档案 ID；车牌档案被物理删除后本列只是历史引用，不设外键。 */
    @Column(name = "plate_id", nullable = false)
    var plateId: Long = 0

    /** 车主档案 ID。 */
    @Column(name = "owner_id", nullable = false)
    var ownerId: Long = 0

    /** 车主姓名快照；车主改名不影响已提交的申请单。 */
    @Column(nullable = false, length = 128)
    lateinit var owner: String

    /** 车主联系电话，科拓月卡的 `tel` 字段取值。 */
    @Column(nullable = false, length = 32)
    lateinit var phone: String

    /**
     * 部门归属：直接落车主档案的部门编码。
     *
     * 申请单本身没有独立的部门选择，归属完全跟随车主，所以这里存编码而不是名称
     * （只有编码唯一，见 [ParkingOwner.departmentCode]）。
     */
    @Column(name = "department_code", length = 64)
    var departmentCode: String? = null

    /** 部门名称快照，用于范围兜底解析与列表展示。 */
    @Column(nullable = false, length = 128)
    lateinit var dept: String

    /** 月卡名称，科拓 `cardInfo.cardName`。 */
    @Column(name = "card_name", nullable = false, length = 64)
    lateinit var cardName: String

    /** 停车区域编码，取值来自 [top.foxball.cartask.entity.type.ZoneType.zoneCode]。 */
    @Column(name = "area_code", length = 64)
    var areaCode: String? = null

    /** 停车区域名称，科拓 `carLotList[].areaName`。 */
    @Column(name = "area_name", length = 128)
    var areaName: String? = null

    /** 月卡有效期开始。 */
    @Column(name = "valid_from", nullable = false)
    lateinit var validFrom: LocalDateTime

    /** 月卡有效期结束。 */
    @Column(name = "valid_to", nullable = false)
    lateinit var validTo: LocalDateTime

    /**
     * 科拓月卡 ID，下发成功后回填。
     *
     * 先查后写的依据：本列非空说明卡已存在，重试下发时只改有效期而不重复发卡。
     */
    @Column(name = "card_id")
    var cardId: Long? = null

    /**
     * 科拓是否已受理过本申请的新增月卡。
     *
     * [cardId] 单独一个字段挡不住「新增成功但没读回卡 ID」这个窗口：那时本地还不知道卡号，
     * 按「查不到卡」重试就会再新增一张，而车场里的卡撤不掉。本列在 `AddCarCardNo` 返回成功后
     * 立刻置位，重试只允许去查卡、不允许再新增。
     */
    @Column(name = "card_issued", nullable = false)
    var cardIssued: Boolean = false

    /** 审批状态。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: Status = Status.PENDING

    /** 科拓下发状态，与 [status] 正交：一条已通过的申请单可能还没下发成功。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status", nullable = false, length = 16)
    var syncStatus: SyncStatus = SyncStatus.NOT_SYNCED

    /** 最近一次下发失败原因；成功后清空。 */
    @Column(name = "sync_message", length = 2048)
    var syncMessage: String? = null

    @Column(name = "apply_time", nullable = false, updatable = false)
    lateinit var applyTime: LocalDateTime

    /** 审批人用户名；未审批时为空。 */
    @Column(name = "reviewed_by", length = 128)
    var reviewedBy: String? = null

    @Column(name = "reviewed_at")
    var reviewedAt: LocalDateTime? = null

    /** 审批意见或驳回原因。 */
    @Column(name = "review_reason", length = 1024)
    var reviewReason: String? = null

    /** 最近一次下发时间。 */
    @Column(name = "synced_at")
    var syncedAt: LocalDateTime? = null

    @Column(nullable = false, updatable = false)
    lateinit var createdAt: LocalDateTime

    @Column(nullable = false)
    lateinit var updatedAt: LocalDateTime

    override val scopeDeptFreeText: String? get() = dept

    override val scopeDeptCode: String? get() = departmentCode

    /** 归一化车牌始终跟随 [plate]，避免各写入点漏赋值导致「同一车牌」的判断失效。 */
    @PrePersist
    @PreUpdate
    fun syncPlateNormalized() {
        plateNormalized = PlateNumbers.normalize(plate)
    }

    enum class Status {
        PENDING, APPROVED, REJECTED, CANCELLED;

        /** 展示文案由后端统一给，避免各端各写一套映射。 */
        @JsonValue
        fun value(): String = when (this) {
            PENDING -> "待审核"
            APPROVED -> "已通过"
            REJECTED -> "已驳回"
            CANCELLED -> "已撤销"
        }
    }

    enum class SyncStatus {
        NOT_SYNCED, SYNCED, FAILED;

        @JsonValue
        fun value(): String = when (this) {
            NOT_SYNCED -> "未下发"
            SYNCED -> "已下发"
            FAILED -> "下发失败"
        }
    }
}
