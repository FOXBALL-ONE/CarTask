package top.foxball.cartask.service.impl

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import top.foxball.cartask.entity.ParkingLot
import top.foxball.cartask.entity.type.ZoneType
import top.foxball.cartask.handler.ParkingAreaSyncInProgressException
import top.foxball.cartask.keytop.KeytopProperties
import top.foxball.cartask.keytop.KeytopService
import top.foxball.cartask.keytop.KeytopSyncRateLimiter
import top.foxball.cartask.repository.ParkingLotRepository
import top.foxball.cartask.repository.ZoneTypeRepository
import top.foxball.cartask.service.ParkingAreaSyncResult
import top.foxball.cartask.service.ParkingAreaSyncService
import java.util.concurrent.locks.ReentrantLock

@Service
class ParkingAreaSyncServiceImpl(
    private val keytopService: KeytopService,
    private val zoneTypeRepository: ZoneTypeRepository,
    private val parkingLotRepository: ParkingLotRepository,
    private val properties: KeytopProperties,
    private val objectMapper: ObjectMapper,
    private val keytopSyncRateLimiter: KeytopSyncRateLimiter? = null,
) : ParkingAreaSyncService {
    @Transactional
    
    
    override fun synchronize(): ParkingAreaSyncResult {
        if (keytopSyncRateLimiter != null && !keytopSyncRateLimiter.hasSnapshot()) {
            return keytopSyncRateLimiter.withSnapshot(keytopSyncRateLimiter.snapshot()) { synchronize() }
        }
        if (!executionLock.tryLock()) {
            throw ParkingAreaSyncInProgressException()
        }
        try {
            val response = keytopService.getParkingPlaceArea()
            require(response.code == 0) {
                "Keytop 停车区域接口返回失败：${response.code ?: "未知"} ${response.message.orEmpty()}".trim()
            }
            
            val container = unwrap(response.data)
            val lotDetail = parseLotDetail(container)
            val areas = parseAreas(container)
            val savedLot = saveLot(lotDetail, areas.size)
            if (areas.isEmpty()) {
                return ParkingAreaSyncResult(
                    receivedCount = 0,
                    createdCount = 0,
                    updatedCount = 0,
                    unchangedCount = 0,
                    lotName = savedLot?.name,
                    totalPlaceCount = lotDetail?.totalPlaceCount,
                )
            }
            
            val existingZones = zoneTypeRepository.findAllByZoneCodeInOrZoneCodeIsNull(areas.keys)
            val existingByCode = existingZones
                .filter { it.zoneCode != null }
                .associateBy { it.zoneCode }
            val legacyZones = existingZones.filter { it.zoneCode == null }
            val consumedLegacyZones = mutableSetOf<ZoneType>()
            val changedZones = mutableListOf<ZoneType>()
            var createdCount = 0
            var updatedCount = 0
            
            areas.forEach { (code, area) ->
                val existing = existingByCode[code] ?: legacyZones.firstOrNull {
                    it !in consumedLegacyZones && it.zoneName == area.name
                }
                if (existing == null) {
                    changedZones += ZoneType().apply {
                        zoneCode = code
                        zoneName = area.name
                        orderNumber = code.toIntOrNull() ?: 0
                        placeCount = area.placeCount
                    }
                    createdCount++
                } else {
                    var changed = false
                    if (existing.zoneCode != code) {
                        existing.zoneCode = code
                        consumedLegacyZones += existing
                        changed = true
                    }
                    if (existing.zoneName != area.name) {
                        existing.zoneName = area.name
                        changed = true
                    }
                    val orderNumber = code.toIntOrNull() ?: existing.orderNumber
                    if (existing.orderNumber != orderNumber) {
                        existing.orderNumber = orderNumber
                        changed = true
                    }
                    if (existing.placeCount != area.placeCount) {
                        existing.placeCount = area.placeCount
                        changed = true
                    }
                    if (changed) {
                        changedZones += existing
                        updatedCount++
                    }
                }
            }
            
            if (changedZones.isNotEmpty()) {
                zoneTypeRepository.saveAll(changedZones)
            }
            return ParkingAreaSyncResult(
                receivedCount = areas.size,
                createdCount = createdCount,
                updatedCount = updatedCount,
                unchangedCount = areas.size - createdCount - updatedCount,
                lotName = savedLot?.name,
                totalPlaceCount = lotDetail?.totalPlaceCount,
            )
        } finally {
            executionLock.unlock()
        }
    }
    
    
    private fun unwrap(data: JsonNode?): JsonNode? {
        if (data == null || data.isNull) return null
        return if (data.isString) objectMapper.readTree(data.asString()) else data
    }
    
    
    private fun parseAreas(container: JsonNode?): Map<String, Area> {
        if (container == null) return emptyMap()
        val areaNode = container.get("areaInfo") ?: container.get("areas") ?: container
        val parsed = if (areaNode.isString) objectMapper.readTree(areaNode.asString()) else areaNode
        if (!parsed.isArray) return emptyMap()
        
        val areas = linkedMapOf<String, Area>()
        parsed.forEach { area ->
            val code = (area.get("areaCode") ?: area.get("area_code"))?.asString()?.trim()
            val name = (area.get("areaName") ?: area.get("area_name"))?.asString()?.trim()
            if (code.isNullOrEmpty() || name.isNullOrEmpty()) {
                logger.warn("忽略缺少区域编码或名称的 Keytop 区域：{}", area)
            } else {
                val placeCount = (area.get("placeCount") ?: area.get("place_count"))?.asInt() ?: 0
                require(placeCount >= 0) { "Keytop 区域车位数量不能为负数：$area" }
                areas[code] = Area(name, placeCount)
            }
        }
        return areas
    }
    
    
    private fun parseLotDetail(container: JsonNode?): LotDetail? {
        if (container == null || !container.isObject) return null
        val totalNode = container.get("totalPlaceCount") ?: container.get("total_place_count")
        val parkAreaNode = container.get("parkArea") ?: container.get("park_area")
        if (totalNode == null && parkAreaNode == null) return null
        val totalPlaceCount = totalNode?.asInt()
        require(totalPlaceCount == null || totalPlaceCount >= 0) { "Keytop 总车位数量不能为负数：$container" }
        return LotDetail(totalPlaceCount, parkAreaNode?.asString())
    }
    
    
    private fun saveLot(detail: LotDetail?, areaCount: Int): ParkingLot? {
        if (detail == null && areaCount == 0) return null
        val parkCode = properties.parkId.trim()
        if (parkCode.isEmpty()) {
            logger.warn("未配置 keytop.park-id，跳过停车场详情保存")
            return null
        }
        val lot = parkingLotRepository.findByParkCode(parkCode) ?: ParkingLot().apply { this.parkCode = parkCode }
        lot.name = properties.parkName.trim().ifEmpty { parkCode }
        detail?.totalPlaceCount?.let { lot.totalPlaceCount = it }
        detail?.parkArea?.let { lot.parkArea = it }
        lot.areaCount = areaCount
        return parkingLotRepository.save(lot)
    }
    
    private data class Area(
        val name: String,
        val placeCount: Int,
    )
    
    private data class LotDetail(
        val totalPlaceCount: Int?,
        val parkArea: String?,
    )
    
    private companion object {
        val logger = LoggerFactory.getLogger(ParkingAreaSyncServiceImpl::class.java)
        val executionLock = ReentrantLock()
    }
}
