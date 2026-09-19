package top.foxball.cartask.task

import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import tools.jackson.databind.ObjectMapper
import top.foxball.cartask.entity.AccessRecord
import top.foxball.cartask.entity.ParkingPlate
import top.foxball.cartask.keytop.KeytopProperties
import top.foxball.cartask.keytop.KeytopResponse
import top.foxball.cartask.keytop.KeytopService
import top.foxball.cartask.repository.AccessRecordRepository
import top.foxball.cartask.repository.ParkingPlateRepository
import top.foxball.cartask.entity.SyncCheckpoint
import top.foxball.cartask.entity.SyncTaskRun
import top.foxball.cartask.service.FileService
import top.foxball.cartask.service.SyncCheckpointService
import top.foxball.cartask.service.SyncTaskHistoryService
import top.foxball.cartask.service.SyncTaskRunCommand
import java.time.Duration
import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class SynCarCapInfoTaskTests {
    private val keytopService = mock<KeytopService>()
    private val repository = mock<AccessRecordRepository>()
    private val fileService = mock<FileService>()
    private val syncCheckpointService = mock<SyncCheckpointService>()
    private val parkingPlateRepository = mock<ParkingPlateRepository>()
    private val historyService = mock<SyncTaskHistoryService>()
    private val objectMapper = ObjectMapper()
    private val task = SynCarCapInfoTask(
        keytopService,
        repository,
        objectMapper,
        KeytopProperties(carCapInfoPageSize = 2),
        fileService,
        syncCheckpointService,
        historyService,
        parkingPlateRepository = parkingPlateRepository,
    )

    @Test
    fun `预检首次同步时报告近一天内待同步记录且不写入检查点`() {
        whenever(syncCheckpointService.find("keytop.car_cap_info")).thenReturn(null)
        whenever(keytopService.getCarInoutInfo(eq(1), eq(1), isNull(), anyOrNull(), anyOrNull())).thenReturn(
            KeytopResponse(0, "success", objectMapper.readTree("""{"totalCount":"42","detailList":[]}""")),
        )

        val result = task.previewSynchronization()

        assertEquals(true, result.initialSync)
        assertEquals(42, result.pendingCount)
        assertEquals(Duration.ofDays(1), Duration.between(result.startTime, result.endTime))
        assertEquals(null, result.checkpointTime)
        verify(syncCheckpointService, never()).save(any())
        verify(repository, never()).save(any())
    }

    @Test
    fun `预检已有检查点时从回看窗口起点查询`() {
        val checkpointTime = LocalDateTime.of(2026, 9, 12, 8, 0)
        whenever(syncCheckpointService.find("keytop.car_cap_info")).thenReturn(
            SyncCheckpoint().apply {
                id = 1
                syncKey = "keytop.car_cap_info"
                cursorTime = checkpointTime
            },
        )
        whenever(keytopService.getCarInoutInfo(eq(1), eq(1), isNull(), anyOrNull(), anyOrNull())).thenReturn(
            KeytopResponse(0, "success", objectMapper.readTree("""{"totalCount":"0","detailList":[]}""")),
        )

        val result = task.previewSynchronization()

        assertEquals(checkpointTime.minusMinutes(30), result.startTime)
        assertEquals(checkpointTime, result.checkpointTime)
    }

    @Test
    fun `无同步快照时同步近一天并写入当前时刻快照`() {
        whenever(syncCheckpointService.loadOrCreate("keytop.car_cap_info")).thenReturn(SyncCheckpoint().apply { syncKey = "keytop.car_cap_info" })
        whenever(repository.findTopByOrderByInAndOutTimeDescIdDesc()).thenReturn(null)
        whenever(
            keytopService.getCarInoutInfo(
                pageIndex = eq(1),
                pageSize = eq(2),
                plateNo = isNull(),
                startTime = anyOrNull(),
                endTime = anyOrNull(),
            ),
        ).thenReturn(
            KeytopResponse(
                0,
                "success",
                objectMapper.readTree(
                    """{"totalCount":"3","detailList":[{"plateNo":"沪A12345","capFlag":0,"capTime":"2026-08-20 10:00:00","cardNo":"CARD-1","passType":1,"operName":"门岗","carOwnerName":"张三"},{"plateNo":"沪B12345","capFlag":1,"capTime":"2026-08-20T10:01:00","passRemark":"正常"}]}""",
                ),
            ),
        )
        whenever(
            keytopService.getCarInoutInfo(
                pageIndex = eq(2),
                pageSize = eq(2),
                plateNo = isNull(),
                startTime = anyOrNull(),
                endTime = anyOrNull(),
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

        val startTimeCaptor = argumentCaptor<LocalDateTime>()
        val endTimeCaptor = argumentCaptor<LocalDateTime>()
        verify(keytopService).getCarInoutInfo(eq(1), eq(2), isNull(), startTimeCaptor.capture(), endTimeCaptor.capture())
        verify(keytopService).getCarInoutInfo(eq(2), eq(2), isNull(), anyOrNull(), eq(endTimeCaptor.firstValue))
        assertEquals(Duration.ofDays(1), Duration.between(startTimeCaptor.firstValue, endTimeCaptor.firstValue))
        verify(repository, times(3)).save(captor.capture())
        val records = captor.allValues
        assertEquals(AccessRecord.InAndOut.IN, records[0].inAndOut)
        assertEquals(AccessRecord.InAndOut.OUT, records[1].inAndOut)
        assertEquals(AccessRecord.InAndOut.IN, records[2].inAndOut)
        assertEquals("CARD-1", records[0].admissionTicketNumber)
        assertEquals("门岗", records[0].operatorName)
        assertEquals(AccessRecord.ReleaseChannel.AUTOMATIC, records[0].releaseChannel)
        assertEquals(LocalDateTime.of(2026, 8, 20, 10, 1), records[1].inAndOutTime)
        val checkpointCaptor = argumentCaptor<SyncCheckpoint>()
        verify(syncCheckpointService, times(2)).save(checkpointCaptor.capture())
        assertEquals(endTimeCaptor.firstValue, checkpointCaptor.lastValue.cursorTime)
    }

    @Test
    fun `同步 imgInfo 图片到本地并保存本地下载地址`() {
        val sourceUrl = "https://image-zos.keytop.cn/591007282/capture.jpg"
        val localUrl = "https://files.example.com/api/files/${UUID.randomUUID()}/download"
        whenever(repository.findTopByOrderByInAndOutTimeDescIdDesc()).thenReturn(null)
        whenever(syncCheckpointService.loadOrCreate("keytop.car_cap_info")).thenReturn(SyncCheckpoint().apply { syncKey = "keytop.car_cap_info" })
        whenever(keytopService.getCarInoutInfo(eq(1), eq(2), isNull(), anyOrNull(), anyOrNull())).thenReturn(
            KeytopResponse(
                0,
                "success",
                objectMapper.readTree(
                    """{"detailList":[{"plateNo":"沪A12345","capFlag":1,"capTime":"2026-08-20 10:00:00","imgInfo":"$sourceUrl"}],"totalCount":"1"}""",
                ),
            ),
        )
        whenever(repository.findByIdentity(any(), any(), any())).thenReturn(null)
        // save 必须回填主键，且后续 findById 要能把它取回来：
        // 图片下载只带着 recordId 去线程池，下载完再按 id 重新读库落状态。
        var persisted: AccessRecord? = null
        whenever(repository.save(any<AccessRecord>())).thenAnswer { invocation ->
            val saved = invocation.getArgument<AccessRecord>(0)
            if (saved.id == null) saved.id = 1L
            persisted = saved
            saved
        }
        whenever(repository.findById(1L)).thenAnswer { Optional.ofNullable(persisted) }
        whenever(fileService.importRemote(eq(sourceUrl), any())).thenReturn(
            FileService.FileData(UUID.randomUUID(), "capture.jpg", "image/jpeg", 3, localUrl, LocalDateTime.now()),
        )

        task.synCarCapInfoList()

        verify(fileService).importRemote(eq(sourceUrl), any())
        assertEquals(localUrl, persisted?.photoUrl)
        assertEquals(AccessRecord.PhotoSyncStatus.LOCAL, persisted?.photoSyncStatus)
    }

    @Test
    fun `缺少快照时忽略本地最新记录并按近一天范围同步`() {
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
        whenever(syncCheckpointService.loadOrCreate("keytop.car_cap_info")).thenReturn(SyncCheckpoint().apply { syncKey = "keytop.car_cap_info" })
        whenever(
            keytopService.getCarInoutInfo(eq(1), eq(2), isNull(), anyOrNull(), anyOrNull()),
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

        val startTimeCaptor = argumentCaptor<LocalDateTime>()
        val endTimeCaptor = argumentCaptor<LocalDateTime>()
        verify(keytopService).getCarInoutInfo(eq(1), eq(2), isNull(), startTimeCaptor.capture(), endTimeCaptor.capture())
        assertEquals(Duration.ofDays(1), Duration.between(startTimeCaptor.firstValue, endTimeCaptor.firstValue))
        verify(repository).save(existing)
        assertEquals("已更新", existing.releaseInstructions)
    }

    @Test
    fun `同步车辆进出记录时按车牌归一化回写车辆类型`() {
        val withBrand = ParkingPlate().apply {
            plate = "沪A·12345"
            carBrand = "旧类型"
        }
        val withoutBrand = ParkingPlate().apply {
            plate = "沪B12345"
            carBrand = "旧类型"
        }
        whenever(parkingPlateRepository.findAll()).thenReturn(listOf(withBrand, withoutBrand))
        whenever(syncCheckpointService.loadOrCreate("keytop.car_cap_info")).thenReturn(SyncCheckpoint().apply { syncKey = "keytop.car_cap_info" })
        whenever(repository.findTopByOrderByInAndOutTimeDescIdDesc()).thenReturn(null)
        whenever(repository.findBySourceRecordId(any())).thenReturn(null)
        whenever(repository.findByIdentity(any(), any(), any())).thenReturn(null)
        whenever(keytopService.getCarInoutInfo(eq(1), eq(2), isNull(), anyOrNull(), anyOrNull())).thenReturn(
            KeytopResponse(
                0,
                "success",
                objectMapper.readTree(
                    """{"totalCount":"2","detailList":[{"plateNo":"沪A12345","capFlag":0,"capTime":"2026-08-20 10:00:00","carBrand":"小型轿车"},{"plateNo":"沪B12345","capFlag":1,"capTime":"2026-08-20 10:01:00"}]}""",
                ),
            ),
        )

        task.synCarCapInfoList()

        assertEquals("小型轿车", withBrand.carBrand)
        assertEquals("", withoutBrand.carBrand)
        verify(parkingPlateRepository, times(2)).save(any())
    }

    @Test
    fun `平台失败时不写入本地`() {
        whenever(repository.findTopByOrderByInAndOutTimeDescIdDesc()).thenReturn(null)
        whenever(syncCheckpointService.loadOrCreate("keytop.car_cap_info")).thenReturn(SyncCheckpoint().apply { syncKey = "keytop.car_cap_info" })
        whenever(keytopService.getCarInoutInfo(eq(1), eq(2), isNull(), anyOrNull(), anyOrNull()))
            .thenReturn(KeytopResponse(1, "failed", null))

        task.synCarCapInfoList()

        verify(repository, never()).save(any())
    }

    @Test
    fun `完整批次成功后将检查点推进到查询截止时间`() {
        val checkpoint = SyncCheckpoint().apply {
            id = 1
            syncKey = "keytop.car_cap_info"
            cursorTime = LocalDateTime.of(2026, 8, 20, 9, 0)
        }
        whenever(syncCheckpointService.loadOrCreate("keytop.car_cap_info")).thenReturn(checkpoint)
        whenever(repository.findTopByOrderByInAndOutTimeDescIdDesc()).thenReturn(null)
        whenever(
            keytopService.getCarInoutInfo(eq(1), eq(2), isNull(), anyOrNull(), anyOrNull()),
        ).thenReturn(
            KeytopResponse(
                0,
                "success",
                objectMapper.readTree("""{"detailList":[{"trafficId":"T-9","plateNo":"沪A99999","capFlag":1,"capTime":"2026-08-20 10:09:00"}],"totalCount":"1"}"""),
            ),
        )
        whenever(repository.findBySourceRecordId("v2|T-9|1|2026-08-20T10:09:00||")).thenReturn(null)
        whenever(repository.findByIdentity(any(), any(), any())).thenReturn(null)

        task.synCarCapInfoList()

        val endTimeCaptor = argumentCaptor<LocalDateTime>()
        verify(keytopService).getCarInoutInfo(eq(1), eq(2), isNull(), eq(LocalDateTime.of(2026, 8, 20, 8, 30)), endTimeCaptor.capture())
        assertEquals(endTimeCaptor.firstValue, checkpoint.cursorTime)
        assertEquals(null, checkpoint.cursorExternalId)
        assertEquals(SyncCheckpoint.Status.SUCCESS, checkpoint.status)
    }

    @Test
    fun `补偿同步查询最近七十二小时且不推进主检查点`() {
        val cursorTime = LocalDateTime.of(2026, 9, 12, 8, 0)
        val checkpoint = SyncCheckpoint().apply {
            id = 1
            syncKey = "keytop.car_cap_info"
            this.cursorTime = cursorTime
            cursorExternalId = "T-8"
            status = SyncCheckpoint.Status.SUCCESS
        }
        whenever(syncCheckpointService.find("keytop.car_cap_info")).thenReturn(checkpoint)
        whenever(keytopService.getCarInoutInfo(eq(1), eq(2), isNull(), anyOrNull(), anyOrNull())).thenReturn(
            KeytopResponse(0, "success", objectMapper.readTree("""{"totalCount":"0","detailList":[]}""")),
        )
        val startTimeCaptor = argumentCaptor<LocalDateTime>()
        val endTimeCaptor = argumentCaptor<LocalDateTime>()
        val command = argumentCaptor<SyncTaskRunCommand>()

        task.reconcileCarCapInfoList()

        verify(keytopService).getCarInoutInfo(
            eq(1),
            eq(2),
            isNull(),
            startTimeCaptor.capture(),
            endTimeCaptor.capture(),
        )
        assertEquals(Duration.ofHours(72), Duration.between(startTimeCaptor.firstValue, endTimeCaptor.firstValue))
        assertEquals(cursorTime, checkpoint.cursorTime)
        assertEquals("T-8", checkpoint.cursorExternalId)
        assertEquals(SyncCheckpoint.Status.SUCCESS, checkpoint.status)
        verify(historyService).record(command.capture())
        assertEquals("car_cap_info.reconciliation", command.firstValue.taskKey)
        assertEquals(SyncTaskRun.Status.SUCCESS, command.firstValue.status)
    }

    @Test
    fun `同步失败不推进已有检查点`() {
        val cursorTime = LocalDateTime.of(2026, 8, 20, 10, 0)
        val checkpoint = SyncCheckpoint().apply {
            id = 1
            syncKey = "keytop.car_cap_info"
            this.cursorTime = cursorTime
            cursorExternalId = "T-8"
            status = SyncCheckpoint.Status.SUCCESS
        }
        whenever(syncCheckpointService.loadOrCreate("keytop.car_cap_info")).thenReturn(checkpoint)
        whenever(syncCheckpointService.find("keytop.car_cap_info")).thenReturn(checkpoint)
        whenever(keytopService.getCarInoutInfo(eq(1), eq(2), isNull(), eq(cursorTime.minusMinutes(30)), anyOrNull()))
            .thenReturn(KeytopResponse(1, "failed", null))

        task.synCarCapInfoList()

        assertEquals(cursorTime, checkpoint.cursorTime)
        assertEquals("T-8", checkpoint.cursorExternalId)
        assertEquals(SyncCheckpoint.Status.FAILED, checkpoint.status)
    }

    @Test
    fun `相同远端流水在同一批次只保存一次`() {
        val checkpoint = SyncCheckpoint().apply {
            id = 1
            syncKey = "keytop.car_cap_info"
            cursorTime = LocalDateTime.of(2026, 8, 20, 9, 0)
        }
        whenever(syncCheckpointService.loadOrCreate("keytop.car_cap_info")).thenReturn(checkpoint)
        whenever(repository.findTopByOrderByInAndOutTimeDescIdDesc()).thenReturn(null)
        whenever(keytopService.getCarInoutInfo(eq(1), eq(2), isNull(), anyOrNull(), anyOrNull())).thenReturn(
            KeytopResponse(
                0,
                "success",
                objectMapper.readTree(
                    """{"totalCount":"2","detailList":[{"trafficId":"T-1","plateNo":"沪A12345","capFlag":0,"capTime":"2026-08-20 10:00:00"},{"trafficId":"T-1","plateNo":"沪A12345","capFlag":0,"capTime":"2026-08-20 10:00:00"}]}""",
                ),
            ),
        )
        whenever(repository.findBySourceRecordId("v2|T-1|0|2026-08-20T10:00:00||")).thenReturn(null)
        whenever(repository.findByIdentity(any(), any(), any())).thenReturn(null)

        task.synCarCapInfoList()

        verify(repository, times(1)).save(any<AccessRecord>())
        val endTimeCaptor = argumentCaptor<LocalDateTime>()
        verify(keytopService).getCarInoutInfo(eq(1), eq(2), isNull(), anyOrNull(), endTimeCaptor.capture())
        assertEquals(endTimeCaptor.firstValue, checkpoint.cursorTime)
        assertEquals(null, checkpoint.cursorExternalId)
    }

    @Test
    fun `相同 trafficId 的不同抓拍分别保存`() {
        val checkpoint = SyncCheckpoint().apply {
            id = 1
            syncKey = "keytop.car_cap_info"
            cursorTime = LocalDateTime.of(2026, 8, 20, 9, 0)
        }
        whenever(syncCheckpointService.loadOrCreate("keytop.car_cap_info")).thenReturn(checkpoint)
        whenever(keytopService.getCarInoutInfo(eq(1), eq(2), isNull(), anyOrNull(), anyOrNull())).thenReturn(
            KeytopResponse(
                0,
                "success",
                objectMapper.readTree(
                    """{"totalCount":"2","detailList":[{"trafficId":"T-1","plateNo":"沪A12345","capFlag":0,"capTime":"2026-08-20 10:00:00","carSerial":"100","nodeId":"1"},{"trafficId":"T-1","plateNo":"沪A12345","capFlag":1,"capTime":"2026-08-20 11:00:00","carSerial":"101","nodeId":"2"}]}""",
                ),
            ),
        )
        whenever(repository.findBySourceRecordId(any())).thenReturn(null)
        whenever(repository.findByIdentity(any(), any(), any())).thenReturn(null)
        val records = argumentCaptor<AccessRecord>()

        task.synCarCapInfoList()

        verify(repository, times(2)).save(records.capture())
        assertEquals("v2|T-1|0|2026-08-20T10:00:00|100|1", records.firstValue.sourceRecordId)
        assertEquals("v2|T-1|1|2026-08-20T11:00:00|101|2", records.secondValue.sourceRecordId)
    }

    @Test
    fun `旧 trafficId 键通过本地身份匹配升级`() {
        val time = LocalDateTime.of(2026, 8, 20, 10, 0)
        val existing = AccessRecord().apply {
            id = 1
            sourceRecordId = "T-1"
            carNumber = "沪A12345"
            inAndOut = AccessRecord.InAndOut.IN
            inAndOutTime = time
        }
        val checkpoint = SyncCheckpoint().apply {
            id = 1
            syncKey = "keytop.car_cap_info"
            cursorTime = LocalDateTime.of(2026, 8, 20, 9, 0)
        }
        whenever(syncCheckpointService.loadOrCreate("keytop.car_cap_info")).thenReturn(checkpoint)
        whenever(keytopService.getCarInoutInfo(eq(1), eq(2), isNull(), anyOrNull(), anyOrNull())).thenReturn(
            KeytopResponse(
                0,
                "success",
                objectMapper.readTree(
                    """{"totalCount":"1","detailList":[{"trafficId":"T-1","plateNo":"沪A12345","capFlag":0,"capTime":"2026-08-20 10:00:00","carSerial":"100","nodeId":"1"}]}""",
                ),
            ),
        )
        whenever(repository.findBySourceRecordId("v2|T-1|0|2026-08-20T10:00:00|100|1")).thenReturn(null)
        whenever(repository.findByIdentity("沪A12345", AccessRecord.InAndOut.IN, time)).thenReturn(existing)

        task.synCarCapInfoList()

        verify(repository).save(existing)
        assertEquals("v2|T-1|0|2026-08-20T10:00:00|100|1", existing.sourceRecordId)
    }

    @Test
    fun `后续分页失败时保留旧检查点游标`() {
        val cursorTime = LocalDateTime.of(2026, 8, 20, 9, 0)
        val checkpoint = SyncCheckpoint().apply {
            id = 1
            syncKey = "keytop.car_cap_info"
            this.cursorTime = cursorTime
            cursorExternalId = "T-0"
        }
        whenever(syncCheckpointService.loadOrCreate("keytop.car_cap_info")).thenReturn(checkpoint)
        whenever(syncCheckpointService.find("keytop.car_cap_info")).thenReturn(checkpoint)
        whenever(keytopService.getCarInoutInfo(eq(1), eq(2), isNull(), eq(cursorTime.minusMinutes(30)), anyOrNull())).thenReturn(
            KeytopResponse(
                0,
                "success",
                objectMapper.readTree(
                    """{"totalCount":"3","detailList":[{"trafficId":"T-1","plateNo":"沪A12345","capFlag":0,"capTime":"2026-08-20 10:00:00"},{"trafficId":"T-2","plateNo":"沪A12346","capFlag":1,"capTime":"2026-08-20 10:01:00"}]}""",
                ),
            ),
        )
        whenever(keytopService.getCarInoutInfo(eq(2), eq(2), isNull(), eq(cursorTime.minusMinutes(30)), anyOrNull()))
            .thenReturn(KeytopResponse(1, "failed", null))
        whenever(repository.findBySourceRecordId(any())).thenReturn(null)
        whenever(repository.findByIdentity(any(), any(), any())).thenReturn(null)

        task.synCarCapInfoList()

        assertEquals(cursorTime, checkpoint.cursorTime)
        assertEquals("T-0", checkpoint.cursorExternalId)
        assertEquals(SyncCheckpoint.Status.FAILED, checkpoint.status)
    }

    @Test
    fun `下次同步重试失败的图片`() {
        val failed = AccessRecord().apply {
            id = 1
            carNumber = "沪A12345"
            inAndOut = AccessRecord.InAndOut.IN
            inAndOutTime = LocalDateTime.of(2026, 8, 20, 10, 0)
            sourcePhotoUrl = "https://image-zos.keytop.cn/591007282/capture.jpg"
            photoUrl = sourcePhotoUrl
            photoSyncStatus = AccessRecord.PhotoSyncStatus.FAILED
        }
        val checkpoint = SyncCheckpoint().apply { id = 1; syncKey = "keytop.car_cap_info" }
        val localUrl = "https://files.example.com/api/files/${UUID.randomUUID()}/download"
        whenever(syncCheckpointService.loadOrCreate("keytop.car_cap_info")).thenReturn(checkpoint)
        whenever(repository.findTopByOrderByInAndOutTimeDescIdDesc()).thenReturn(null)
        whenever(
            repository.findTop100ByPhotoSyncStatusInOrderByIdAsc(
                listOf(AccessRecord.PhotoSyncStatus.FAILED, AccessRecord.PhotoSyncStatus.PENDING),
            ),
        ).thenReturn(listOf(failed))
        whenever(repository.findById(1L)).thenReturn(Optional.of(failed))
        whenever(fileService.importRemote(eq(failed.sourcePhotoUrl!!), any())).thenReturn(
            FileService.FileData(UUID.randomUUID(), "capture.jpg", "image/jpeg", 3, localUrl, LocalDateTime.now()),
        )
        whenever(keytopService.getCarInoutInfo(eq(1), eq(2), isNull(), anyOrNull(), anyOrNull()))
            .thenReturn(KeytopResponse(0, "success", objectMapper.readTree("""{"totalCount":"0","detailList":[]}""")))

        task.synCarCapInfoList()

        assertEquals(localUrl, failed.photoUrl)
        assertEquals(AccessRecord.PhotoSyncStatus.LOCAL, failed.photoSyncStatus)
        assertEquals(null, failed.photoSyncError)
    }


    @Test
    fun `待下载状态的图片在下次同步时继续处理`() {
        val pending = AccessRecord().apply {
            id = 7
            carNumber = "沪A12345"
            inAndOut = AccessRecord.InAndOut.IN
            inAndOutTime = LocalDateTime.of(2026, 8, 20, 10, 0)
            sourcePhotoUrl = "https://image-zos.keytop.cn/591007282/pending.jpg"
            photoUrl = sourcePhotoUrl
            photoSyncStatus = AccessRecord.PhotoSyncStatus.PENDING
        }
        whenever(syncCheckpointService.loadOrCreate("keytop.car_cap_info")).thenReturn(
            SyncCheckpoint().apply { id = 1; syncKey = "keytop.car_cap_info" },
        )
        whenever(repository.findTopByOrderByInAndOutTimeDescIdDesc()).thenReturn(null)
        whenever(
            repository.findTop100ByPhotoSyncStatusInOrderByIdAsc(
                listOf(AccessRecord.PhotoSyncStatus.FAILED, AccessRecord.PhotoSyncStatus.PENDING),
            ),
        ).thenReturn(listOf(pending))
        whenever(repository.findById(7L)).thenReturn(Optional.of(pending))
        whenever(fileService.importRemote(eq(pending.sourcePhotoUrl!!), any())).thenReturn(
            FileService.FileData(UUID.randomUUID(), "capture.jpg", "image/jpeg", 3, "https://files.example.com/p.jpg", LocalDateTime.now()),
        )
        whenever(keytopService.getCarInoutInfo(eq(1), eq(2), isNull(), anyOrNull(), anyOrNull()))
            .thenReturn(KeytopResponse(0, "success", objectMapper.readTree("""{"totalCount":"0","detailList":[]}""")))

        task.synCarCapInfoList()

        assertEquals(AccessRecord.PhotoSyncStatus.LOCAL, pending.photoSyncStatus)
    }


    @Test
    fun `有图片地址的新记录先落成待下载再由下载线程改成已落地`() {
        val sourceUrl = "https://image-zos.keytop.cn/591007282/new.jpg"
        val localUrl = "https://files.example.com/new.jpg"
        whenever(syncCheckpointService.loadOrCreate("keytop.car_cap_info")).thenReturn(
            SyncCheckpoint().apply { id = 1; syncKey = "keytop.car_cap_info" },
        )
        whenever(repository.findTopByOrderByInAndOutTimeDescIdDesc()).thenReturn(null)
        whenever(keytopService.getCarInoutInfo(eq(1), eq(2), isNull(), anyOrNull(), anyOrNull())).thenReturn(
            KeytopResponse(
                0,
                "success",
                objectMapper.readTree(
                    """{"totalCount":"1","detailList":[{"trafficId":"T-NEW","plateNo":"沪A12345","capFlag":0,"capTime":"2026-08-20 10:00:00","imgInfo":"$sourceUrl"}]}""",
                ),
            ),
        )
        whenever(repository.findBySourceRecordId(any())).thenReturn(null)
        whenever(repository.findByIdentity(any(), any(), any())).thenReturn(null)
        // 记录入库那一刻的状态：必须先是 PENDING，而不是等下载完才写状态
        val statusWhenSaved = mutableListOf<AccessRecord.PhotoSyncStatus?>()
        var persisted: AccessRecord? = null
        whenever(repository.save(any<AccessRecord>())).thenAnswer { invocation ->
            val saved = invocation.getArgument<AccessRecord>(0)
            if (saved.id == null) saved.id = 3L
            statusWhenSaved.add(saved.photoSyncStatus)
            persisted = saved
            saved
        }
        whenever(repository.findById(3L)).thenAnswer { Optional.ofNullable(persisted) }
        whenever(fileService.importRemote(eq(sourceUrl), any())).thenReturn(
            FileService.FileData(UUID.randomUUID(), "new.jpg", "image/jpeg", 3, localUrl, LocalDateTime.now()),
        )

        task.synCarCapInfoList()

        assertEquals(AccessRecord.PhotoSyncStatus.PENDING, statusWhenSaved.first())
        assertEquals(AccessRecord.PhotoSyncStatus.LOCAL, persisted?.photoSyncStatus)
    }


    @Test
    fun `同一页里的重复记录只提交一次图片下载`() {
        val sourceUrl = "https://image-zos.keytop.cn/591007282/dup.jpg"
        whenever(syncCheckpointService.loadOrCreate("keytop.car_cap_info")).thenReturn(
            SyncCheckpoint().apply { id = 1; syncKey = "keytop.car_cap_info" },
        )
        whenever(repository.findTopByOrderByInAndOutTimeDescIdDesc()).thenReturn(null)
        whenever(keytopService.getCarInoutInfo(eq(1), eq(2), isNull(), anyOrNull(), anyOrNull())).thenReturn(
            KeytopResponse(
                0,
                "success",
                objectMapper.readTree(
                    """{"totalCount":"2","detailList":[
                        {"trafficId":"T-DUP","plateNo":"沪A12345","capFlag":0,"capTime":"2026-08-20 10:00:00","imgInfo":"$sourceUrl"},
                        {"trafficId":"T-DUP","plateNo":"沪A12345","capFlag":0,"capTime":"2026-08-20 10:00:00","imgInfo":"$sourceUrl"}]}""",
                ),
            ),
        )
        whenever(repository.findBySourceRecordId(any())).thenReturn(null)
        whenever(repository.findByIdentity(any(), any(), any())).thenReturn(null)
        var persisted: AccessRecord? = null
        whenever(repository.save(any<AccessRecord>())).thenAnswer { invocation ->
            val saved = invocation.getArgument<AccessRecord>(0)
            if (saved.id == null) saved.id = 5L
            persisted = saved
            saved
        }
        whenever(repository.findById(5L)).thenAnswer { Optional.ofNullable(persisted) }
        whenever(fileService.importRemote(eq(sourceUrl), any())).thenReturn(
            FileService.FileData(UUID.randomUUID(), "dup.jpg", "image/jpeg", 3, "https://files.example.com/dup.jpg", LocalDateTime.now()),
        )

        task.synCarCapInfoList()

        verify(fileService, times(1)).importRemote(eq(sourceUrl), any())
    }

    @Test
    fun `定时同步成功后记录定时执行历史`() {
        val checkpoint = SyncCheckpoint().apply { id = 1; syncKey = "keytop.car_cap_info" }
        whenever(syncCheckpointService.loadOrCreate("keytop.car_cap_info")).thenReturn(checkpoint)
        whenever(repository.findTopByOrderByInAndOutTimeDescIdDesc()).thenReturn(null)
        whenever(keytopService.getCarInoutInfo(eq(1), eq(2), isNull(), anyOrNull(), anyOrNull())).thenReturn(
            KeytopResponse(
                0,
                "success",
                objectMapper.readTree("""{"totalCount":"1","detailList":[{"plateNo":"沪A12345","capFlag":0,"capTime":"2026-08-20 10:00:00"}]}"""),
            ),
        )
        whenever(repository.findBySourceRecordId(any())).thenReturn(null)
        whenever(repository.findByIdentity(any(), any(), any())).thenReturn(null)
        val command = argumentCaptor<SyncTaskRunCommand>()

        task.synCarCapInfoList()

        verify(historyService).record(command.capture())
        assertEquals("car_cap_info.sync", command.firstValue.taskKey)
        assertEquals("车辆进出记录同步", command.firstValue.taskName)
        assertEquals(SyncTaskRun.Trigger.SCHEDULED, command.firstValue.trigger)
        assertEquals(SyncTaskRun.Status.SUCCESS, command.firstValue.status)
        assertEquals(1, command.firstValue.processedCount)
    }

    @Test
    fun `手动同步失败时记录失败执行历史并抛出异常`() {
        val checkpoint = SyncCheckpoint().apply { id = 1; syncKey = "keytop.car_cap_info" }
        whenever(syncCheckpointService.loadOrCreate("keytop.car_cap_info")).thenReturn(checkpoint)
        whenever(keytopService.getCarInoutInfo(eq(1), eq(2), isNull(), anyOrNull(), anyOrNull()))
            .thenReturn(KeytopResponse(1, "failed", null))
        val command = argumentCaptor<SyncTaskRunCommand>()

        val exception = runCatching { task.synchronize() }.exceptionOrNull()

        assertEquals(true, exception is IllegalArgumentException)
        verify(historyService).record(command.capture())
        assertEquals(SyncTaskRun.Trigger.MANUAL, command.firstValue.trigger)
        assertEquals(SyncTaskRun.Status.FAILED, command.firstValue.status)
    }
}
