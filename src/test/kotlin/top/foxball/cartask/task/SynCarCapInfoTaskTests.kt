package top.foxball.cartask.task

import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.scheduling.annotation.Scheduled
import tools.jackson.databind.ObjectMapper
import top.foxball.cartask.entity.AccessRecord
import top.foxball.cartask.keytop.KeytopProperties
import top.foxball.cartask.keytop.KeytopResponse
import top.foxball.cartask.keytop.KeytopService
import top.foxball.cartask.repository.AccessRecordRepository
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SynCarCapInfoTaskTests {
    private val keytopService = mock<KeytopService>()
    private val repository = mock<AccessRecordRepository>()
    private val objectMapper = ObjectMapper()
    private val task = SynCarCapInfoTask(
        keytopService,
        repository,
        objectMapper,
        KeytopProperties(carCapInfoPageSize = 2),
    )

    @Test
    fun `按配置的上海时区 cron 执行`() {
        val scheduled = SynCarCapInfoTask::class.java
            .getDeclaredMethod("synCarCapInfoList")
            .getAnnotation(Scheduled::class.java)

        assertEquals("#{@keytopProperties.carCapInfoSyncCron}", scheduled.cron)
        assertEquals("Asia/Shanghai", scheduled.zone)
    }

    @Test
    fun `首次同步分页解析并写入车辆进出记录`() {
        whenever(repository.findTopByOrderByInAndOutTimeDescIdDesc()).thenReturn(null)
        whenever(
            keytopService.getCarInoutInfo(
                pageIndex = 1,
                pageSize = 2,
                startTime = null,
            ),
        ).thenReturn(
            KeytopResponse(
                0,
                "success",
                objectMapper.readTree(
                    """{"totalCount":"3","detailList":[{"plateNo":"沪A12345","capFlag":1,"capTime":"2026-08-20 10:00:00","cardNo":"CARD-1","passType":1,"operName":"门岗","carOwnerName":"张三"},{"plateNo":"沪B12345","capFlag":2,"capTime":"2026-08-20T10:01:00","passRemark":"正常"}]}""",
                ),
            ),
        )
        whenever(
            keytopService.getCarInoutInfo(
                pageIndex = 2,
                pageSize = 2,
                startTime = null,
            ),
        ).thenReturn(
            KeytopResponse(
                0,
                "success",
                objectMapper.readTree(
                    """{"detailList":[{"plateNo":"沪C12345","capFlag":"抓拍","capTime":"2026-08-20 10:02:00"}]}""",
                ),
            ),
        )
        whenever(repository.findByIdentity(any(), any(), any())).thenReturn(null)
        val captor = argumentCaptor<AccessRecord>()

        task.synCarCapInfoList()

        verify(keytopService).getCarInoutInfo(1, 2, null, null, null)
        verify(keytopService).getCarInoutInfo(2, 2, null, null, null)
        verify(repository, times(3)).save(captor.capture())
        val records = captor.allValues
        assertEquals(AccessRecord.InAndOut.IN, records[0].inAndOut)
        assertEquals(AccessRecord.InAndOut.OUT, records[1].inAndOut)
        assertEquals(AccessRecord.InAndOut.IN, records[2].inAndOut)
        assertEquals("CARD-1", records[0].admissionTicketNumber)
        assertEquals("门岗", records[0].operatorName)
        assertEquals(AccessRecord.ReleaseChannel.AUTOMATIC, records[0].releaseChannel)
        assertEquals(LocalDateTime.of(2026, 8, 20, 10, 1), records[1].inAndOutTime)
    }

    @Test
    fun `以本地最新时间增量同步并按复合键更新`() {
        val latest = AccessRecord().apply {
            id = 9
            carNumber = "沪A12345"
            inAndOut = AccessRecord.InAndOut.IN
            inAndOutTime = LocalDateTime.of(2026, 8, 20, 9, 0)
        }
        val existing = AccessRecord().apply {
            id = 10
            carNumber = "沪A12345"
            inAndOut = AccessRecord.InAndOut.OUT
            inAndOutTime = LocalDateTime.of(2026, 8, 20, 9, 30)
        }
        whenever(repository.findTopByOrderByInAndOutTimeDescIdDesc()).thenReturn(latest)
        whenever(
            keytopService.getCarInoutInfo(
                pageIndex = 1,
                pageSize = 2,
                startTime = latest.inAndOutTime.minusMinutes(5),
            ),
        ).thenReturn(
            KeytopResponse(
                0,
                "success",
                objectMapper.readTree(
                    """{"detailList":[{"plateNo":"沪A12345","capFlag":2,"capTime":"2026-08-20 09:30:00","passRemark":"已更新"}]}""",
                ),
            ),
        )
        whenever(
            repository.findByIdentity("沪A12345", AccessRecord.InAndOut.OUT, existing.inAndOutTime),
        )
            .thenReturn(existing)

        task.synCarCapInfoList()

        verify(keytopService).getCarInoutInfo(1, 2, null, latest.inAndOutTime.minusMinutes(5), null)
        verify(repository).save(existing)
        assertEquals("已更新", existing.releaseInstructions)
    }

    @Test
    fun `平台失败时不写入本地`() {
        whenever(repository.findTopByOrderByInAndOutTimeDescIdDesc()).thenReturn(null)
        whenever(keytopService.getCarInoutInfo(pageIndex = 1, pageSize = 2, startTime = null))
            .thenReturn(KeytopResponse(1, "failed", null))

        task.synCarCapInfoList()

        verify(repository, never()).save(any())
    }
}
