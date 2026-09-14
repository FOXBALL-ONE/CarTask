package top.foxball.cartask.repository

import org.springframework.data.jpa.repository.JpaRepository
import top.foxball.cartask.entity.VehicleInoutRequest

interface VehicleInoutRequestRepository : JpaRepository<VehicleInoutRequest, Long> {

    /**
     * 同一车牌是否已有未决申请（待审核或已通过待下发）。
     *
     * 一律按**归一化车牌**比较：车牌档案是人工维护的，「京A·12345」与「京A12345」是同一辆车，
     * 按字面量比较等于给同一辆车留了第二条申请的口子，最终在科拓侧多出一张月卡。
     */
    fun existsByPlateNormalizedAndStatusIn(
        plateNormalized: String,
        statuses: Collection<VehicleInoutRequest.Status>,
    ): Boolean

    /** 同一车牌是否存在其它未决申请，用于编辑时排除自身。 */
    fun existsByPlateNormalizedAndStatusInAndIdNot(
        plateNormalized: String,
        statuses: Collection<VehicleInoutRequest.Status>,
        id: Long,
    ): Boolean
}
