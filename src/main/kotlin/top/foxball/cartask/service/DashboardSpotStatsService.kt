package top.foxball.cartask.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import top.foxball.cartask.config.DashboardProperties
import top.foxball.cartask.entity.type.ZoneType
import top.foxball.cartask.repository.ParkingSpotRepository
import top.foxball.cartask.repository.ZoneTypeRepository
import top.foxball.cartask.scope.DataScope
import top.foxball.cartask.scope.ScopeQuerySupport

/** 单个车场/区域的车位统计。 */
data class DashboardZoneStats(
    val zoneName: String,
    /** 该区域的车位容量，取自科拓同步的区域车位数。 */
    val total: Int,
    /** 该区域内本地已登记且启用的车位数。 */
    val used: Int,
)

/** 仪表盘的车位统计。 */
data class DashboardSpotStats(
    /** 配置的车场/区域的容量之和。 */
    val total: Int,
    val used: Int,
    /** 已配置且已同步到的区域，按显示顺序排列。 */
    val zones: List<DashboardZoneStats>,
)

/**
 * 仪表盘的车位统计。
 *
 * 只统计配置的车场/区域（默认区域编码 1、2），而不是全库汇总：库里可能有多个车场，
 * 全库相加没有业务含义。
 *
 * 口径上刻意分两个来源：
 * - **车位总数取科拓同步的区域容量**（[ZoneType.placeCount]）。本地 parking_spot 表只登记了
 *   有车主的车位，直接数行数会明显小于车场实际容量，这正是原先「车位总数」不准的原因。
 * - **已分配取本地已登记且启用的车位数**，并按调用者的数据范围过滤。
 */
@Service
class DashboardSpotStatsService(
    private val zoneTypeRepository: ZoneTypeRepository,
    private val parkingSpotRepository: ParkingSpotRepository,
    private val scopeQuerySupport: ScopeQuerySupport,
    private val properties: DashboardProperties,
) {
    @Transactional(readOnly = true)
            /**
             * currentStats：查询或读取相关数据。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @param scope 参与本次处理的输入参数。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun currentStats(scope: DataScope): DashboardSpotStats {
        val zones = zoneTypeRepository.findAllByZoneCodeIn(properties.zoneCodes)
            // 停用的区域不计入容量：区域都停用了还把它的车位算进来会虚高。
            .filter { it.status == ZoneType.Status.Activity }
            .sortedWith(compareBy({ it.orderNumber }, { it.id }))

        val zoneNames = zones.mapNotNull { it.zoneName?.trim()?.takeIf(String::isNotEmpty) }.toSet()
        // 车位的区域字段存的是**名称**而不是编码（parking_spot.area 是自由文本），只能按名称关联。
        val spotsInZones = if (zoneNames.isEmpty()) {
            emptyList()
        } else {
            val inZones = parkingSpotRepository.findAll().filter { it.area in zoneNames }
            if (scope.unrestricted) {
                inZones
            } else {
                val visibleOwnerCodes = scopeQuerySupport.ownerCardIdsInScope(scope)
                inZones.filter { scopeQuerySupport.spotVisible(visibleOwnerCodes, it.ownerCode) }
            }
        }

        val zoneStats = zones.map { zone ->
            val name = zone.zoneName.orEmpty()
            DashboardZoneStats(
                zoneName = name,
                total = zone.placeCount,
                used = spotsInZones.count { it.area == name && it.status == STATUS_ENABLED },
            )
        }
        return DashboardSpotStats(
            total = zoneStats.sumOf { it.total },
            // 用各区域之和而不是再数一遍，保证「已分配」与柱状图逐项相加一致。
            used = zoneStats.sumOf { it.used },
            zones = zoneStats,
        )
    }

    private companion object {
        const val STATUS_ENABLED = 1
    }
}
