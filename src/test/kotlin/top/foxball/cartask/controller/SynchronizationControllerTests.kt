package top.foxball.cartask.controller

import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.access.prepost.PreAuthorize
import tools.jackson.databind.ObjectMapper
import top.foxball.cartask.service.ParkingAreaSyncResult
import top.foxball.cartask.service.ParkingAreaSyncService
import top.foxball.cartask.shared.ResponseBuilder
import top.foxball.cartask.task.CarCapInfoSyncResult
import top.foxball.cartask.task.CarCapInfoSyncPreview
import top.foxball.cartask.task.SynCarCapInfoTask
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SynchronizationControllerTests {
    private val syncService = mock<ParkingAreaSyncService>()
    private val carCapInfoTask = mock<SynCarCapInfoTask>()
    private val controller = SynchronizationController(syncService, carCapInfoTask, ResponseBuilder())

    @Test
    fun `手动执行停车区域同步并返回统计`() {
        whenever(syncService.synchronize()).thenReturn(ParkingAreaSyncResult(5, 2, 1, 2))

        val response = controller.synchronizeParkingAreas()

        verify(syncService).synchronize()
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
            "(hasRole('SUPER_ADMIN') or hasRole('ADMIN')) and hasAuthority('dictionary:sync')",
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
            "(hasRole('SUPER_ADMIN') or hasRole('ADMIN')) and hasAuthority('vehicle-record:sync')",
            annotation.value,
        )
    }

    @Test
    fun `车辆进出预检要求独立同步权限`() {
        val annotation = SynchronizationController::class.java
            .getDeclaredMethod("previewAccessRecordSynchronization")
            .getAnnotation(PreAuthorize::class.java)

        assertEquals(
            "(hasRole('SUPER_ADMIN') or hasRole('ADMIN')) and hasAuthority('vehicle-record:sync')",
            annotation.value,
        )
    }
}
