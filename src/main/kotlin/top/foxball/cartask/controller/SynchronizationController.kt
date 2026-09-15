package top.foxball.cartask.controller

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import top.foxball.cartask.handler.ParamErrorException
import top.foxball.cartask.service.SyncScheduleService
import top.foxball.cartask.service.SyncTaskHistoryService
import top.foxball.cartask.service.SyncTaskProgressService
import top.foxball.cartask.shared.Response
import top.foxball.cartask.shared.ResponseBuilder
import top.foxball.cartask.task.SynAccountGenerateTask
import top.foxball.cartask.task.SynAreaInfoTask
import top.foxball.cartask.task.SynCarCapInfoTask
import top.foxball.cartask.task.SynOwnerArchiveTask
import java.time.LocalDateTime

/** 修改同步周期的请求体。 */
class UpdateSyncScheduleRequest {
    /** Spring 六段式 cron（秒 分 时 日 月 周）。 */
    @JsonProperty("cron")
    var cron: String? = null
}

data class ScheduleData(
    @param:JsonProperty("task_key") val taskKey: String,
    @param:JsonProperty("task_name") val taskName: String,
    val description: String,
    val cron: String,
    @param:JsonProperty("default_cron") val defaultCron: String,
    /** 与配置默认值不同时为 true；改回默认值会删掉覆盖记录。 */
    val customized: Boolean,
    @param:JsonProperty("updated_at") val updatedAt: LocalDateTime?,
    @param:JsonProperty("updated_by") val updatedBy: String?,
    @param:JsonProperty("next_run_at") val nextRunAt: LocalDateTime?,
)

@RestController
@RequestMapping("/api/synchronizations")
class SynchronizationController(
    private val synAreaInfoTask: SynAreaInfoTask,
    private val synCarCapInfoTask: SynCarCapInfoTask,
    private val synOwnerArchiveTask: SynOwnerArchiveTask,
    private val synAccountGenerateTask: SynAccountGenerateTask,
    private val syncTaskHistoryService: SyncTaskHistoryService,
    private val syncScheduleService: SyncScheduleService,
    private val responseBuilder: ResponseBuilder,
    private val syncTaskProgressService: SyncTaskProgressService = SyncTaskProgressService(),
) {
    @GetMapping("/progress")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAnyAuthority('dictionary:sync', 'vehicle-record:sync', 'owner:sync', 'account:sync')")
            /**
             * syncProgress：执行数据同步、探测或文件处理。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun syncProgress(): ResponseEntity<Response> {
        data class TaskData(
            @param:JsonProperty("task_key") val taskKey: String,
            @param:JsonProperty("task_name") val taskName: String,
            val running: Boolean,
            @param:JsonProperty("processed_count") val processedCount: Int,
            @param:JsonProperty("total_count") val totalCount: Int?,
            @param:JsonProperty("started_at") val startedAt: LocalDateTime?,
        )

        val rs = syncTaskProgressService.snapshot()
            .map { TaskData(it.taskKey, it.taskName, it.running, it.processedCount, it.totalCount, it.startedAt) }
        return responseBuilder.ok().data(rs).build()
    }

    /** 从科拓拉取一次停车区域，并将结果幂等写入本地区域字典。 */
    @PostMapping("/parking-areas")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('dictionary:sync')")
    fun synchronizeParkingAreas(): ResponseEntity<Response> {
        data class Response(
            @param:JsonProperty("received_count") val receivedCount: Int,
            @param:JsonProperty("created_count") val createdCount: Int,
            @param:JsonProperty("updated_count") val updatedCount: Int,
            @param:JsonProperty("unchanged_count") val unchangedCount: Int,
            @param:JsonProperty("executed_at") val executedAt: LocalDateTime,
        )

        val result = synAreaInfoTask.synchronize()
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
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('vehicle-record:sync')")
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
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('vehicle-record:sync')")
            /**
             * synchronizeAccessRecords：执行数据同步、探测或文件处理。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
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

    /** 从车辆进出记录里补建系统缺失的车主信息：回查科拓卡片信息新建车主档案并登记车牌关联。 */
    @PostMapping("/owners")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('owner:sync')")
    fun synchronizeOwners(): ResponseEntity<Response> {
        data class Response(
            @param:JsonProperty("created_owner_count") val createdOwnerCount: Int,
            @param:JsonProperty("linked_plate_count") val linkedPlateCount: Int,
            @param:JsonProperty("skipped_count") val skippedCount: Int,
            @param:JsonProperty("executed_at") val executedAt: LocalDateTime,
        )

        val result = synOwnerArchiveTask.generate()
        val rs = Response(
            createdOwnerCount = result.createdOwnerCount,
            linkedPlateCount = result.linkedPlateCount,
            skippedCount = result.skippedCount,
            executedAt = LocalDateTime.now(),
        )
        return responseBuilder.ok()
            .message("车主信息补建完成")
            .data(rs)
            .build()
    }

    /** 根据车辆进出记录为业主补建平台登录账号，已有账号的业主自动跳过。 */
    @PostMapping("/accounts")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('account:sync')")
    fun synchronizeAccounts(): ResponseEntity<Response> {
        data class Response(
            @param:JsonProperty("created_count") val createdCount: Int,
            @param:JsonProperty("skipped_count") val skippedCount: Int,
            @param:JsonProperty("failed_count") val failedCount: Int,
            @param:JsonProperty("executed_at") val executedAt: LocalDateTime,
        )

        val result = synAccountGenerateTask.generate()
        val rs = Response(
            createdCount = result.createdCount,
            skippedCount = result.skippedCount,
            failedCount = result.failedCount,
            executedAt = LocalDateTime.now(),
        )
        return responseBuilder.ok()
            .message("车辆业主账号生成完成")
            .data(rs)
            .build()
    }

    /** 分页查询数据同步任务的执行历史，可按任务标识过滤。 */
    @GetMapping("/history")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('DEPT_ADMIN')) and hasAuthority('sync-history:read')")
    fun syncHistory(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(name = "page_size", defaultValue = "20") pageSize: Int,
        @RequestParam(name = "task_key", required = false) taskKey: String?,
    ): ResponseEntity<Response> {
        data class RunData(
            val id: Long,
            @param:JsonProperty("task_key") val taskKey: String,
            @param:JsonProperty("task_name") val taskName: String,
            val trigger: String,
            val status: String,
            @param:JsonProperty("source_system") val sourceSystem: String,
            @param:JsonProperty("actor_username") val actorUsername: String,
            @param:JsonProperty("started_at") val startedAt: LocalDateTime,
            @param:JsonProperty("finished_at") val finishedAt: LocalDateTime,
            @param:JsonProperty("duration_ms") val durationMs: Long,
            @param:JsonProperty("processed_count") val processedCount: Int?,
            @param:JsonProperty("local_photo_count") val localPhotoCount: Int?,
            @param:JsonProperty("failed_photo_count") val failedPhotoCount: Int?,
            val summary: String?,
            val error: String?,
        )

        data class Response(
            val runs: List<RunData>,
            val page: Int,
            @param:JsonProperty("page_size") val pageSize: Int,
            val total: Long,
        )

        val result = syncTaskHistoryService.list(page, pageSize, taskKey)
        val rs = Response(
            result.runs.map { run ->
                RunData(
                    requireNotNull(run.id),
                    run.taskKey,
                    run.taskName,
                    run.trigger.name,
                    run.status.name,
                    run.sourceSystem,
                    run.actorUsername,
                    run.startedAt,
                    run.finishedAt,
                    run.durationMs,
                    run.processedCount,
                    run.localPhotoCount,
                    run.failedPhotoCount,
                    run.summary,
                    run.error,
                )
            },
            result.page,
            result.pageSize,
            result.total,
        )
        return responseBuilder.ok().data(rs).build()
    }

    /** 列出全部可调周期的同步任务：当前周期、配置默认值与下次触发时间。 */
    @GetMapping("/schedules")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN')) and hasAuthority('sync-schedule:manage')")
    fun syncSchedules(): ResponseEntity<Response> {
        return responseBuilder.ok()
            .data(syncScheduleService.list().map(::toScheduleData))
            .build()
    }

    /**
     * 修改某个同步任务的执行周期。保存即生效：事务提交后调度器重新注册触发时间。
     *
     * [taskKey] 里带点（如 `car_cap_info.sync`），Spring 默认不再做后缀匹配，无需转义。
     */
    @PutMapping("/schedules/{taskKey}")
    @PreAuthorize("(hasRole('SUPER_ADMIN') or hasRole('ADMIN')) and hasAuthority('sync-schedule:manage')")
    fun updateSyncSchedule(
        @PathVariable taskKey: String,
        @RequestBody request: UpdateSyncScheduleRequest,
    ): ResponseEntity<Response> {
        val cron = request.cron?.trim() ?: throw ParamErrorException("cron 表达式不能为空")
        val updated = syncScheduleService.update(taskKey, cron)
        return responseBuilder.ok()
            .message("同步周期已更新：" + updated.taskName)
            .data(toScheduleData(updated))
            .build()
    }

    /**
     * toScheduleData：转换、构建或格式化数据。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param schedule 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    private fun toScheduleData(schedule: SyncScheduleService.ScheduleView): ScheduleData = ScheduleData(
        taskKey = schedule.taskKey,
        taskName = schedule.taskName,
        description = schedule.description,
        cron = schedule.cron,
        defaultCron = schedule.defaultCron,
        customized = schedule.customized,
        updatedAt = schedule.updatedAt,
        updatedBy = schedule.updatedBy,
        nextRunAt = schedule.nextRunAt,
    )
}
