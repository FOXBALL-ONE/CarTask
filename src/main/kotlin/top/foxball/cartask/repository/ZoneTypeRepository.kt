package top.foxball.cartask.repository

import org.springframework.data.jpa.repository.JpaRepository
import top.foxball.cartask.entity.type.ZoneType

interface ZoneTypeRepository : JpaRepository<ZoneType, Long> {
    fun findByZoneCode(zoneCode: String): ZoneType?

    /** 按区域编码批量查询；仪表盘按配置的车场编码汇总时使用。 */
    fun findAllByZoneCodeIn(zoneCodes: Collection<String>): List<ZoneType>

    fun findAllByZoneCodeInOrZoneCodeIsNull(zoneCodes: Collection<String>): List<ZoneType>
}
