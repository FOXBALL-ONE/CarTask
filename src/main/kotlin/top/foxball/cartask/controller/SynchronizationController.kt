package top.foxball.cartask.controller

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import top.foxball.cartask.service.ParkingAreaSyncService
import top.foxball.cartask.shared.Response
import top.foxball.cartask.shared.ResponseBuilder
import top.foxball.cartask.task.SynCarCapInfoTask
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/synchronizations")
class SynchronizationController(
    private val parkingAreaSyncService: ParkingAreaSyncService,
    private val synCarCapInfoTask: SynCarCapInfoTask,
    private val responseBuilder: ResponseBuilder,
) {
    /** 从科拓拉取一次停车区域，并将结果幂等写入本地区域字典。 */
    @PostMapping("/parking-areas")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN')) and hasAuthority('dictionary:sync')")
    fun synchronizeParkingAreas(): ResponseEntity<Response> {
        data class Response(
            @param:JsonProperty("received_count") val receivedCount: Int,
            @param:JsonProperty("created_count") val createdCount: Int,
            @param:JsonProperty("updated_count") val updatedCount: Int,
            @param:JsonProperty("unchanged_count") val unchangedCount: Int,
            @param:JsonProperty("executed_at") val executedAt: LocalDateTime,
        )

        val result = parkingAreaSyncService.synchronize()
        val rs = Response(
            receivedCount = result.receivedCount,
            createdCount = result.createdCount,
            updatedCount = result.updatedCount,
            unchangedCount = result.unchangedCount,
            executedAt = LocalDateTime.now(),
        )
        return responseBuilder.ok()
            .message("停车区域同步完成")
            .data(rs)
            .build()
    }

    /** 根据上次成功检查点，从科拓增量同步车辆进出记录与抓拍图片。 */
    @GetMapping("/access-records/preview")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN')) and hasAuthority('vehicle-record:sync')")
    fun previewAccessRecordSynchronization(): ResponseEntity<Response> {
        data class Response(
            @param:JsonProperty("initial_sync") val initialSync: Boolean,
            @param:JsonProperty("pending_count") val pendingCount: Int?,
            @param:JsonProperty("start_time") val startTime: LocalDateTime,
            @param:JsonProperty("end_time") val endTime: LocalDateTime,
            @param:JsonProperty("checkpoint_time") val checkpointTime: LocalDateTime?,
            @param:JsonProperty("checked_at") val checkedAt: LocalDateTime,
        )

        val result = synCarCapInfoTask.previewSynchronization()
        val rs = Response(
            initialSync = result.initialSync,
            pendingCount = result.pendingCount,
            startTime = result.startTime,
            endTime = result.endTime,
            checkpointTime = result.checkpointTime,
            checkedAt = LocalDateTime.now(),
        )
        return responseBuilder.ok()
            .message("车辆进出记录同步预检完成")
            .data(rs)
            .build()
    }

    @PostMapping("/access-records")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN')) and hasAuthority('vehicle-record:sync')")
    fun synchronizeAccessRecords(): ResponseEntity<Response> {
        data class Response(
            @param:JsonProperty("processed_count") val processedCount: Int,
            @param:JsonProperty("local_photo_count") val localPhotoCount: Int,
            @param:JsonProperty("failed_photo_count") val failedPhotoCount: Int,
            @param:JsonProperty("start_time") val startTime: LocalDateTime,
            @param:JsonProperty("cursor_time") val cursorTime: LocalDateTime,
            @param:JsonProperty("executed_at") val executedAt: LocalDateTime,
        )

        val result = synCarCapInfoTask.synchronize()
        val rs = Response(
            processedCount = result.processedCount,
            localPhotoCount = result.localPhotoCount,
            failedPhotoCount = result.failedPhotoCount,
            startTime = result.startTime,
            cursorTime = result.cursorTime,
            executedAt = LocalDateTime.now(),
        )
        return responseBuilder.ok()
            .message("车辆进出记录同步完成")
            .data(rs)
            .build()
    }
}
