package top.foxball.cartask.task

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import top.foxball.cartask.entity.type.ZoneType
import top.foxball.cartask.keytop.KeytopProperties
import top.foxball.cartask.keytop.KeytopService
import top.foxball.cartask.repository.ZoneTypeRepository
import java.util.concurrent.locks.ReentrantLock

/** 从科拓同步停车区域，维护系统可用的区域字典。 */
@Component
class SynAreaInfoTask(
    private val keytopService: KeytopService,
    private val zoneTypeRepository: ZoneTypeRepository,
    private val objectMapper: ObjectMapper,
    private val keytopProperties: KeytopProperties,
) {
    @Scheduled(cron = "#{@keytopProperties.areaSyncCron}", zone = "Asia/Shanghai")
    @Transactional
    fun synAreaInfo() {
        if (!executionLock.tryLock()) {
            logger.warn("停车区域同步仍在执行，本次跳过")
            return
        }
        try {
            val response = keytopService.getParkingPlaceArea()
            require(response.code == 0) {
                "Keytop 停车区域接口返回失败：${response.code ?: "未知"} ${response.message.orEmpty()}".trim()
            }
            val areas = parseAreas(response.data)
            if (areas.isEmpty()) {
                logger.warn("Keytop 停车区域接口返回为空，本次不更新区域字典")
                return
            }

            val existingZones = zoneTypeRepository
                .findAllByZoneCodeInOrZoneCodeIsNull(areas.keys)
            val existingByCode = existingZones.filter { it.zoneCode != null }.associateBy { it.zoneCode }
            val legacyZones = existingZones.filter { it.zoneCode == null }
            val newZones = mutableListOf<ZoneType>()
            val changedZones = mutableListOf<ZoneType>()
            val consumedLegacyZones = mutableSetOf<ZoneType>()
            areas.forEach { (code, name) ->
                val existing = existingByCode[code] ?: legacyZones
                    .firstOrNull { it !in consumedLegacyZones && it.zoneName == name }
                if (existing == null) {
                    newZones += ZoneType().apply {
                        zoneCode = code
                        zoneName = name
                        orderNumber = code.toIntOrNull() ?: 0
                    }
                } else {
                    var changed = false
                    if (existing.zoneCode != code) {
                        existing.zoneCode = code
                        consumedLegacyZones += existing
                        changed = true
                    }
                    if (existing.zoneName != name) {
                        existing.zoneName = name
                        changed = true
                    }
                    val order = code.toIntOrNull() ?: existing.orderNumber
                    if (existing.orderNumber != order) {
                        existing.orderNumber = order
                        changed = true
                    }
                    if (changed) changedZones += existing
                }
            }
            if (newZones.isNotEmpty()) zoneTypeRepository.saveAll(newZones)
            if (changedZones.isNotEmpty()) zoneTypeRepository.saveAll(changedZones)
            val createdCount = newZones.size
            val updatedCount = changedZones.size
            logger.info("停车区域同步完成：新增 {} 个，更新 {} 个", createdCount, updatedCount)
        } catch (exception: RuntimeException) {
            logger.error("停车区域同步失败", exception)
        } finally {
            executionLock.unlock()
        }
    }

    private fun parseAreas(data: JsonNode?): Map<String, String> {
        if (data == null || data.isNull) return emptyMap()
        val container = if (data.isTextual) objectMapper.readTree(data.asString()) else data
        val areaNode = container.get("areaInfo") ?: container.get("areas") ?: container
        val parsed = if (areaNode.isTextual) objectMapper.readTree(areaNode.asString()) else areaNode
        if (!parsed.isArray) return emptyMap()
        return parsed.toList().mapNotNull { area ->
            val code = area.get("areaCode")?.asString()?.trim()
                ?: area.get("area_code")?.asString()?.trim()
            val name = area.get("areaName")?.asString()?.trim()
                ?: area.get("area_name")?.asString()?.trim()
            if (code.isNullOrEmpty() || name.isNullOrEmpty()) {
                logger.warn("忽略缺少区域编码或名称的 Keytop 区域：{}", area)
                null
            } else code to name
        }.toMap()
    }

    private companion object {
        val logger = LoggerFactory.getLogger(SynAreaInfoTask::class.java)
        val executionLock = ReentrantLock()
    }
}
