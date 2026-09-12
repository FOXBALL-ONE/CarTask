package top.foxball.cartask.shared

import java.time.LocalDate

/**
 * 车辆年检的判定规则。
 *
 * 列表、导出、导入三处都要给同一批车牌算出「是否已年检」「有效期到哪天」：只要有一处口径不同，
 * 就会出现「导入显示已年检、列表却说未年检」的错乱，所以只保留这一个实现。
 */
object VehicleInspection {
    /**
     * 已年检但没登记年检有效期时的默认有效期：年检日期起一年。
     *
     * 年检本身是一年一次的例行检验，导入的年检表通常只填年检日期，有效期按此推算。
     */
    fun defaultValidUntil(inspectedOn: LocalDate): LocalDate = inspectedOn.plusYears(1)

    /** 是否已年检：登记了年检日期或年检有效期即视为已年检。 */
    fun inspected(inspectionDate: LocalDate?, validUntil: LocalDate?): Boolean =
        inspectionDate != null || validUntil != null

    /**
     * 年检状态文案。
     *
     * 只登记了年检日期而没有有效期时按有效处理：没有有效期就没有过期依据，凭空判定过期
     * 会让用户看到一条无法解释的红色标记。
     */
    fun status(inspectionDate: LocalDate?, validUntil: LocalDate?, today: LocalDate): String = when {
        !inspected(inspectionDate, validUntil) -> "未年检"
        validUntil != null && validUntil.isBefore(today) -> "已过期"
        else -> "有效"
    }
}
