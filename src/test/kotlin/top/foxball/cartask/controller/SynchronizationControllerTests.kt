package top.foxball.cartask.controller

import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.access.prepost.PreAuthorize
import tools.jackson.databind.ObjectMapper
import top.foxball.cartask.entity.SyncTaskRun
import top.foxball.cartask.service.ParkingAreaSyncResult
import top.foxball.cartask.service.SyncTaskHistoryService
import top.foxball.cartask.service.SyncTaskRunPage
import top.foxball.cartask.shared.ResponseBuilder
import top.foxball.cartask.task.AccountGenerateResult
import top.foxball.cartask.task.CarCapInfoSyncResult
import top.foxball.cartask.task.CarCapInfoSyncPreview
import top.foxball.cartask.task.OwnerArchiveResult
import top.foxball.cartask.task.SynAccountGenerateTask
import top.foxball.cartask.task.SynAreaInfoTask
import top.foxball.cartask.task.SynCarCapInfoTask
import top.foxball.cartask.task.SynOwnerArchiveTask
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SynchronizationControllerTests {
    private val areaInfoTask = mock<SynAreaInfoTask>()
    private val carCapInfoTask = mock<SynCarCapInfoTask>()
    private val ownerArchiveTask = mock<SynOwnerArchiveTask>()
    private val accountGenerateTask = mock<SynAccountGenerateTask>()
    private val historyService = mock<SyncTaskHistoryService>()
    private val controller = SynchronizationController(
        areaInfoTask,
        carCapInfoTask,
        ownerArchiveTask,
        accountGenerateTask,
        historyService,
        ResponseBuilder(),
    )

    @Test
    fun `手动执行停车区域同步并返回统计`() {
        whenever(areaInfoTask.synchronize()).thenReturn(ParkingAreaSyncResult(5, 2, 1, 2))

        val response = controller.synchronizeParkingAreas()

        verify(areaInfoTask).synchronize()
        assertEquals(200, response.statusCode.value())
        assertEquals("停车区域同步完成", response.body?.message)
        val data = ObjectMapper().valueToTree<tools.jackson.databind.JsonNode>(response.body?.data)
        assertEquals(5, data.get("received_count").asInt())
        assertEquals(2, data.get("created_count").asInt())
        assertEquals(1, data.get("updated_count").asInt())
        assertEquals(2, data.get("unchanged_count").asInt())
        assertNotNull(data.get("executed_at").asString())
    }

    @Test
    fun `手动同步要求管理角色和独立同步权限`() {
        val annotation = SynchronizationController::class.java
            .getDeclaredMethod("synchronizeParkingAreas")
            .getAnnotation(PreAuthorize::class.java)

        assertEquals(
            "(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('dictionary:sync')",
            annotation.value,
        )
    }

    @Test
    fun `手动执行车辆进出记录增量同步并返回检查点`() {
        val startTime = LocalDateTime.of(2026, 9, 11, 8, 0)
        val cursorTime = LocalDateTime.of(2026, 9, 11, 8, 5)
        whenever(carCapInfoTask.synchronize()).thenReturn(
            CarCapInfoSyncResult(
                processedCount = 8,
                localPhotoCount = 7,
                failedPhotoCount = 1,
                startTime = startTime,
                cursorTime = cursorTime,
            ),
        )

        val response = controller.synchronizeAccessRecords()

        verify(carCapInfoTask).synchronize()
        assertEquals(200, response.statusCode.value())
        assertEquals("车辆进出记录同步完成", response.body?.message)
        val data = ObjectMapper().valueToTree<tools.jackson.databind.JsonNode>(response.body?.data)
        assertEquals(8, data.get("processed_count").asInt())
        assertEquals(7, data.get("local_photo_count").asInt())
        assertEquals(1, data.get("failed_photo_count").asInt())
        assertEquals(startTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), data.get("start_time").asString())
        assertEquals(cursorTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), data.get("cursor_time").asString())
        assertNotNull(data.get("executed_at").asString())
    }

    @Test
    fun `预检车辆进出同步并返回待同步记录数`() {
        val checkpointTime = LocalDateTime.of(2026, 9, 10, 8, 0)
        val startTime = checkpointTime
        val endTime = LocalDateTime.of(2026, 9, 11, 8, 0)
        whenever(carCapInfoTask.previewSynchronization()).thenReturn(
            CarCapInfoSyncPreview(
                initialSync = false,
                pendingCount = 24,
                startTime = startTime,
                endTime = endTime,
                checkpointTime = checkpointTime,
            ),
        )

        val response = controller.previewAccessRecordSynchronization()

        verify(carCapInfoTask).previewSynchronization()
        assertEquals(200, response.statusCode.value())
        assertEquals("车辆进出记录同步预检完成", response.body?.message)
        val data = ObjectMapper().valueToTree<tools.jackson.databind.JsonNode>(response.body?.data)
        assertEquals(false, data.get("initial_sync").asBoolean())
        assertEquals(24, data.get("pending_count").asInt())
        assertEquals(startTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), data.get("start_time").asString())
        assertEquals(endTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), data.get("end_time").asString())
        assertEquals(checkpointTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), data.get("checkpoint_time").asString())
        assertNotNull(data.get("checked_at").asString())
    }

    @Test
    fun `手动车辆进出同步要求独立同步权限`() {
        val annotation = SynchronizationController::class.java
            .getDeclaredMethod("synchronizeAccessRecords")
            .getAnnotation(PreAuthorize::class.java)

        assertEquals(
            "(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('vehicle-record:sync')",
            annotation.value,
        )
    }

    @Test
    fun `车辆进出预检要求独立同步权限`() {
        val annotation = SynchronizationController::class.java
            .getDeclaredMethod("previewAccessRecordSynchronization")
            .getAnnotation(PreAuthorize::class.java)

        assertEquals(
            "(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('vehicle-record:sync')",
            annotation.value,
        )
    }

    @Test
    fun `手动补建车主信息并返回统计`() {
        whenever(ownerArchiveTask.generate()).thenReturn(OwnerArchiveResult(2, 3, 4, LocalDateTime.now()))

        val response = controller.synchronizeOwners()

        verify(ownerArchiveTask).generate()
        assertEquals(200, response.statusCode.value())
        assertEquals("车主信息补建完成", response.body?.message)
        val data = ObjectMapper().valueToTree<tools.jackson.databind.JsonNode>(response.body?.data)
        assertEquals(2, data.get("created_owner_count").asInt())
        assertEquals(3, data.get("linked_plate_count").asInt())
        assertEquals(4, data.get("skipped_count").asInt())
        assertNotNull(data.get("executed_at").asString())
    }

    @Test
    fun `手动补建车主信息要求独立同步权限`() {
        val annotation = SynchronizationController::class.java
            .getDeclaredMethod("synchronizeOwners")
            .getAnnotation(PreAuthorize::class.java)

        assertEquals(
            "(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('owner:sync')",
            annotation.value,
        )
    }

    @Test
    fun `手动执行车辆业主账号生成并返回统计`() {
        whenever(accountGenerateTask.generate()).thenReturn(AccountGenerateResult(3, 5, 1, LocalDateTime.now()))

        val response = controller.synchronizeAccounts()

        verify(accountGenerateTask).generate()
        assertEquals(200, response.statusCode.value())
        assertEquals("车辆业主账号生成完成", response.body?.message)
        val data = ObjectMapper().valueToTree<tools.jackson.databind.JsonNode>(response.body?.data)
        assertEquals(3, data.get("created_count").asInt())
        assertEquals(5, data.get("skipped_count").asInt())
        assertEquals(1, data.get("failed_count").asInt())
        assertNotNull(data.get("executed_at").asString())
    }

    @Test
    fun `手动生成车辆业主账号要求独立同步权限`() {
        val annotation = SynchronizationController::class.java
            .getDeclaredMethod("synchronizeAccounts")
            .getAnnotation(PreAuthorize::class.java)

        assertEquals(
            "(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('account:sync')",
            annotation.value,
        )
    }

    @Test
    fun `分页查询同步执行历史`() {
        val run = SyncTaskRun().apply {
            id = 7L
            taskKey = "account.generate"
            taskName = "车辆业主账号生成"
            trigger = SyncTaskRun.Trigger.MANUAL
            status = SyncTaskRun.Status.SUCCESS
            sourceSystem = "WEB"
            actorUsername = "admin"
            startedAt = LocalDateTime.of(2026, 9, 11, 9, 0)
            finishedAt = LocalDateTime.of(2026, 9, 11, 9, 0, 5)
            durationMs = 5000
            processedCount = 3
            summary = "创建 3 个，跳过 5 个，失败 1 个"
        }
        whenever(historyService.list(2, 20, "account.generate"))
            .thenReturn(SyncTaskRunPage(listOf(run), 2, 20, 41))

        val response = controller.syncHistory(2, 20, "account.generate")

        verify(historyService).list(2, 20, "account.generate")
        assertEquals(200, response.statusCode.value())
        val data = ObjectMapper().valueToTree<tools.jackson.databind.JsonNode>(response.body?.data)
        assertEquals(41, data.get("total").asLong())
        assertEquals(20, data.get("page_size").asInt())
        val first = data.get("runs").get(0)
        assertEquals("account.generate", first.get("task_key").asString())
        assertEquals("MANUAL", first.get("trigger").asString())
        assertEquals("SUCCESS", first.get("status").asString())
        assertEquals("admin", first.get("actor_username").asString())
        assertEquals(5000, first.get("duration_ms").asLong())
        assertEquals(3, first.get("processed_count").asInt())
        assertEquals("创建 3 个，跳过 5 个，失败 1 个", first.get("summary").asString())
    }

    @Test
    fun `查询同步执行历史要求独立查看权限`() {
        val annotation = SynchronizationController::class.java
            .getDeclaredMethod("syncHistory", Int::class.java, Int::class.java, String::class.java)
            .getAnnotation(PreAuthorize::class.java)

        assertEquals(
            "(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('sync-history:read')",
            annotation.value,
        )
    }
}
