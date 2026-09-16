package top.foxball.cartask.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import top.foxball.cartask.config.DashboardProperties
import top.foxball.cartask.entity.type.ZoneType
import top.foxball.cartask.repository.ParkingSpotRepository
import top.foxball.cartask.repository.ZoneTypeRepository
import top.foxball.cartask.scope.DataScope
import top.foxball.cartask.scope.ScopeQuerySupport


data class DashboardZoneStats(
    val zoneName: String,
    
    val total: Int,
    
    val used: Int,
)


data class DashboardSpotStats(
    
    val total: Int,
    val used: Int,
    
    val zones: List<DashboardZoneStats>,
)


@Service
class DashboardSpotStatsService(
    private val zoneTypeRepository: ZoneTypeRepository,
    private val parkingSpotRepository: ParkingSpotRepository,
    private val scopeQuerySupport: ScopeQuerySupport,
    private val properties: DashboardProperties,
) {
    @Transactional(readOnly = true)
    
    
    fun currentStats(scope: DataScope): DashboardSpotStats {
        val zones = zoneTypeRepository.findAllByZoneCodeIn(properties.zoneCodes)
            .filter { it.status == ZoneType.Status.Activity }
            .sortedWith(compareBy({ it.orderNumber }, { it.id }))
        
        val zoneNames = zones.mapNotNull { it.zoneName?.trim()?.takeIf(String::isNotEmpty) }.toSet()
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
            used = zoneStats.sumOf { it.used },
            zones = zoneStats,
        )
    }
    
    private companion object {
        const val STATUS_ENABLED = 1
    }
}
