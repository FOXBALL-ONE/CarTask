package top.foxball.cartask.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime
import java.math.BigDecimal
import top.foxball.cartask.entity.type.CarType
import top.foxball.cartask.shared.PlateNumbers

/** 车辆进出门禁的流水记录。 */
@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(
    name = "access_record",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_access_record_source_record_id", columnNames = ["source_record_id"]),
    ],
)
class AccessRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    /** 车辆号牌；无牌车辆可为空。 */
    @Column(name = "car_number", length = 64)
    var carNumber: String? = null

    /**
     * 归一化后的号牌，由 [syncCarNumberNormalized] 在写入时自动维护。
     *
     * 范围查询要在 SQL 里用 `IN` 匹配车牌，而 [carNumber] 是科拓原始格式、车牌档案是人工格式，
     * 两边存在间隔符差异，直接比较会漏。历史数据为 null，由回填接口补齐。
     * 用生命周期回调而不是在每个写入点赋值，是为了不依赖调用方记得——漏一处就是数据泄露。
     */
    @Column(name = "car_number_normalized", length = 64)
    var carNumberNormalized: String? = null

    /** 科拓抓拍流水的稳定复合标识；由 trafficId、抓拍方向、时间、通道流水等字段组成。 */
    @Column(name = "source_record_id", length = 256)
    var sourceRecordId: String? = null

    /** 车辆所属部门名称；由同步接口直接保存快照，避免部门变更影响历史记录展示。 */
    @Column(name = "department_name", length = 128)
    var departmentName: String? = null

    /** 进场或出场方向。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "in_and_out", nullable = false, length = 8)
    var inAndOut: InAndOut = InAndOut.IN

    /** 实际进出时间。 */
    @Column(name = "in_and_out_time", nullable = false)
    lateinit var inAndOutTime: LocalDateTime

    /** 车辆类型关联。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "car_type_id")
    var carType: CarType? = null

    /** 车辆类型名称快照；同步接口没有本地字典关联时使用。 */
    @Column(name = "vehicle_type_name", length = 64)
    var vehicleTypeName: String? = null

    /** 无牌车辆入场时使用的票据或卡号。 */
    @Column(name = "admission_ticket_number", length = 128)
    var admissionTicketNumber: String? = null

    /** 放行类型原始展示文本，例如“自动放行”。 */
    @Column(name = "pass_type", length = 64)
    var passType: String? = null

    /** 放行指令或审核说明。 */
    @Column(name = "release_instructions", length = 512)
    var releaseInstructions: String? = null

    /** 放行渠道。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "release_channel", length = 16)
    var releaseChannel: ReleaseChannel? = null

    /** 执行放行操作的人员。 */
    @Column(name = "operator_name", length = 128)
    var operatorName: String? = null

    /** 车主姓名。 */
    @Column(name = "car_owner_name", length = 128)
    var carOwnerName: String? = null

    /** 车辆通过的闸口或车场入口名称。 */
    @Column(name = "gate_name", length = 128)
    var gateName: String? = null

    /** 车辆进出抓拍图片地址。 */
    @Column(name = "photo_url", length = 1024)
    var photoUrl: String? = null

    /** 科拓返回的原始抓拍图片地址，用于本地下载失败后的补偿。 */
    @Column(name = "source_photo_url", length = 1024)
    var sourcePhotoUrl: String? = null

    /** 抓拍图片落地到本地文件存储的状态。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "photo_sync_status", length = 16)
    var photoSyncStatus: PhotoSyncStatus? = null

    /** 最近一次图片下载失败原因；成功后清空。 */
    @Column(name = "photo_sync_error", length = 2048)
    var photoSyncError: String? = null

    /** 通行收费金额。 */
    @Column(name = "fee_amount", nullable = false, precision = 18, scale = 2)
    var feeAmount: BigDecimal = BigDecimal.ZERO

    /** 通行记录状态，例如“正常”或“异常”。 */
    @Column(name = "record_status", nullable = false, length = 16)
    var recordStatus: String = "正常"

    /** 记录创建时间。 */
    @Column(nullable = false, updatable = false)
    lateinit var createdAt: LocalDateTime

    /** 进出方向。 */
    enum class InAndOut {
        IN,
        OUT,
    }

    /** 号牌归一化结果始终跟随 [carNumber]，避免各写入点漏赋值导致范围查询漏数据。 */
    @PrePersist
    @PreUpdate
    fun syncCarNumberNormalized() {
        carNumberNormalized = PlateNumbers.normalize(carNumber)
    }

    /** 放行来源渠道。 */
    enum class ReleaseChannel {
        AUTOMATIC,
        MANUAL,
        REMOTE,
        UNKNOWN,
    }

    enum class PhotoSyncStatus {
        LOCAL,
        FAILED,
        NOT_AVAILABLE,
    }

    companion object {
        /** 科拓车辆类型数字编码转展示名称：0-临时车、1-月卡车、2-储值车、3-免费车，其余按原文保留。 */
        fun displayVehicleTypeName(raw: String?): String? = when (raw) {
            null -> null
            "0" -> "临时车"
            "1" -> "月卡车"
            "2" -> "储值车"
            "3" -> "免费车"
            else -> raw
        }
    }
}
