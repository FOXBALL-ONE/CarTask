package top.foxball.cartask.task

import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.scheduling.annotation.Scheduled
import tools.jackson.databind.ObjectMapper
import top.foxball.cartask.entity.type.ZoneType
import top.foxball.cartask.keytop.KeytopResponse
import top.foxball.cartask.keytop.KeytopProperties
import top.foxball.cartask.keytop.KeytopService
import top.foxball.cartask.repository.ZoneTypeRepository
import kotlin.test.Test
import kotlin.test.assertEquals

class SynAreaInfoTaskTests {
    private val keytopService = mock<KeytopService>()
    private val repository = mock<ZoneTypeRepository>()
    private val task = SynAreaInfoTask(keytopService, repository, ObjectMapper(), KeytopProperties())

    @Test
    fun `每天上海时区凌晨两点执行`() {
        val scheduled = SynAreaInfoTask::class.java
            .getDeclaredMethod("synAreaInfo")
            .getAnnotation(Scheduled::class.java)

        assertEquals("\${keytop.area-sync-cron:0 0 2 * * *}", scheduled.cron)
        assertEquals("Asia/Shanghai", scheduled.zone)
    }

    @Test
    fun `新增并更新科拓停车区域`() {
        whenever(keytopService.getParkingPlaceArea()).thenReturn(
            KeytopResponse(
                code = 0,
                message = "success",
                data = ObjectMapper().readTree("""{"areaInfo":[{"areaCode":1,"areaName":"地面区"},{"areaCode":2,"areaName":"地下区"}]}"""),
            ),
        )
        val changed = ZoneType().apply {
            zoneCode = "2"
            zoneName = "旧名称"
            orderNumber = 99
        }
        whenever(repository.findAllByZoneCodeInOrZoneCodeIsNull(setOf("1", "2"))).thenReturn(listOf(changed))
        val captor = argumentCaptor<List<ZoneType>>()

        task.synAreaInfo()

        verify(repository, org.mockito.kotlin.times(2)).saveAll(captor.capture())
        assertEquals("1", captor.allValues.first().first().zoneCode)
        assertEquals("地面区", captor.allValues.first().first().zoneName)
        assertEquals("2", changed.zoneCode)
        assertEquals("地下区", changed.zoneName)
        assertEquals(2, changed.orderNumber)
    }

    @Test
    fun `科拓返回失败时不写入字典`() {
        whenever(keytopService.getParkingPlaceArea()).thenReturn(KeytopResponse(1, "failed", null))

        task.synAreaInfo()

        verify(repository, never()).save(any())
    }
}
