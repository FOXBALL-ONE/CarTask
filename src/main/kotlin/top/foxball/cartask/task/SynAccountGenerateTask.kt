package top.foxball.cartask.task

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import top.foxball.cartask.audit.AuditRequestContext
import top.foxball.cartask.entity.SyncTaskRun
import top.foxball.cartask.handler.AccountGenerateInProgressException
import top.foxball.cartask.repository.AccessRecordRepository
import top.foxball.cartask.repository.ParkingOwnerRepository
import top.foxball.cartask.repository.ParkingPlateRepository
import top.foxball.cartask.service.SyncTaskHistoryService
import top.foxball.cartask.service.SyncTaskRunCommand
import top.foxball.cartask.service.SyncTaskProgressService
import top.foxball.cartask.service.UserService
import java.time.LocalDateTime
import java.util.concurrent.locks.ReentrantLock

data class AccountGenerateResult(
    val createdCount: Int,
    val skippedCount: Int,
    val failedCount: Int,
    val executedAt: LocalDateTime,
)

/**
 * 为已有车主档案的业主补建平台登录账号：以近 [ACTIVE_WINDOW_DAYS] 天内有进出记录的车牌为数据源
 * （数据库层面按车牌去重），经车牌档案（ParkingPlate）关联车主档案（ParkingOwner），
 * 以车主手机号作为登录名创建账号，已有账号自动跳过。
 *
 * 账号的所属部门取自车主档案的部门字段：同名部门已存在则直接挂靠，不存在则按「部门名称 + 自动编码」
 * 新建，使账号归属不依赖预先存在的部门数据。
 *
 * 账号的所属部门取自车主档案的部门字段：同名部门已存在则直接挂靠，不存在则按「部门名称 + 自动编码」
 * 新建，使账号归属不依赖预先存在的部门数据。
 *
 * 车主档案缺失或已停用的车牌由 [SynOwnerArchiveTask] 在更早的时间点单独补建，本任务只负责账号阶段，
 * 因此不访问科拓接口；补建任务跑过之后，下一次执行本任务即可覆盖到新车主。
 */
@Component
class SynAccountGenerateTask(
    private val userService: UserService,
    private val parkingPlateRepository: ParkingPlateRepository,
    private val parkingOwnerRepository: ParkingOwnerRepository,
    private val accessRecordRepository: AccessRecordRepository,
    private val syncTaskHistoryService: SyncTaskHistoryService,
    private val syncTaskProgressService: SyncTaskProgressService = SyncTaskProgressService(),
) {
    @Scheduled(cron = "\${app.account-generate-cron:0 0 3 * * *}", zone = "Asia/Shanghai")
    fun synAccountGenerate() {
        AuditRequestContext.withRun {
            try {
                generate(SyncTaskRun.Trigger.SCHEDULED)
            } catch (exception: AccountGenerateInProgressException) {
                logger.warn("车辆业主账号生成仍在执行，本次定时任务跳过")
            } catch (exception: RuntimeException) {
                logger.error("车辆业主账号生成失败", exception)
            }
        }
    }

    /** 手动执行一次账号生成。 */
    @Transactional(noRollbackFor = [RuntimeException::class])
    fun generate(): AccountGenerateResult = generate(SyncTaskRun.Trigger.MANUAL)

    private fun generate(trigger: SyncTaskRun.Trigger): AccountGenerateResult {
        if (!executionLock.tryLock()) {
            throw AccountGenerateInProgressException()
        }
        val startedAt = LocalDateTime.now()
        syncTaskProgressService.start(TASK_KEY, TASK_NAME, startedAt)
        try {
            val result = generateInternal(startedAt)
            recordHistory(trigger, SyncTaskRun.Status.SUCCESS, startedAt, result) {
                "创建 ${result.createdCount} 个，跳过 ${result.skippedCount} 个，失败 ${result.failedCount} 个"
            }
            return result
        } catch (exception: RuntimeException) {
            recordHistory(trigger, SyncTaskRun.Status.FAILED, startedAt, null) {
                exception.message?.take(2048) ?: exception.javaClass.simpleName
            }
            throw exception
        } finally {
            syncTaskProgressService.finish(TASK_KEY, startedAt)
            executionLock.unlock()
        }
    }

    private fun generateInternal(startedAt: LocalDateTime): AccountGenerateResult {
        var createdCount = 0
        var skippedCount = 0
        var failedCount = 0
        val processedPhones = mutableSetOf<String>()

        val activePlates = accessRecordRepository
            .findDistinctCarNumbersSince(startedAt.minusDays(ACTIVE_WINDOW_DAYS))
            .mapNotNull(::normalizePlate)
            .toSet()
        syncTaskProgressService.update(TASK_KEY, 0, activePlates.size)
        if (activePlates.isEmpty()) {
            logger.info("近 {} 天没有车辆进出记录，本次不生成账号", ACTIVE_WINDOW_DAYS)
            return AccountGenerateResult(0, 0, 0, LocalDateTime.now())
        }

        val plateByKey = parkingPlateRepository.findAll()
            .asSequence()
            .filter { it.status == STATUS_ENABLED }
            .mapNotNull { plate -> normalizePlate(plate.plate)?.let { it to plate } }
            .toMap()
        val ownerById = parkingOwnerRepository
            .findAllById(plateByKey.values.map { it.ownerId }.toSet())
            .filter { it.status == STATUS_ENABLED }
            .associateBy { requireNotNull(it.id) }
            .toMutableMap()
        val existingUsernames = userService.findExistingUsernames(
            ownerById.values.mapNotNull { it.phone.trim().takeIf(String::isNotEmpty) }.toSet(),
        )

        activePlates.forEachIndexed { index, plate ->
            syncTaskProgressService.update(TASK_KEY, index, activePlates.size)
            val parkingPlate = plateByKey[plate] ?: run {
                skippedCount++
                logger.warn("跳过没有有效车牌档案的活跃车牌：{}，请先执行车主档案补建", plate)
                return@forEachIndexed
            }
            val owner = ownerById[parkingPlate.ownerId] ?: run {
                skippedCount++
                logger.warn("跳过已停用或缺失车主档案的车牌：{}，车主 ID: {}", plate, parkingPlate.ownerId)
                return@forEachIndexed
            }
            val phone = owner.phone.trim().takeIf(String::isNotEmpty) ?: run {
                skippedCount++
                logger.warn("跳过无手机号的车主：{}，车主 ID: {}", owner.name, owner.id)
                return@forEachIndexed
            }
            if (!processedPhones.add(phone)) {
                skippedCount++
                return@forEachIndexed
            }
            val nickName = owner.name.trim()
            if (nickName.isEmpty()) {
                skippedCount++
                logger.warn("跳过无姓名的车主，车主 ID: {}", owner.id)
                return@forEachIndexed
            }
            if (phone in existingUsernames) {
                skippedCount++
                return@forEachIndexed
            }
            try {
                userService.create(
                    UserService.CreateCommand(
                        username = phone,
                        email = "${phone}@auto.local",
                        credential = INITIAL_PASSWORD,
                        phone = phone,
                        departmentId = DEPARTMENT_ID,
                        nickName = nickName,
                    ),
                )
                createdCount++
            } catch (exception: RuntimeException) {
                failedCount++
                logger.error("为车辆业主创建平台账号失败，车主 ID: {}, 手机号: {}", owner.id, phone, exception)
            }
        }
        syncTaskProgressService.update(TASK_KEY, activePlates.size, activePlates.size)

        logger.info("车辆业主账号生成完成：创建 {} 个，跳过 {} 个，失败 {} 个", createdCount, skippedCount, failedCount)
        return AccountGenerateResult(createdCount, skippedCount, failedCount, LocalDateTime.now())
    }

    /**
     * 车牌归一化：进出记录的车牌来自科拓，车牌档案由人工或导入维护，两边可能存在
     * 间隔符（·）和空格差异，匹配前统一去除并转大写。
     */
    private fun normalizePlate(value: String?): String? = value
        ?.replace(SEPARATOR_PATTERN, "")
        ?.trim()
        ?.uppercase()
        ?.takeIf(String::isNotEmpty)

    private fun recordHistory(
        trigger: SyncTaskRun.Trigger,
        status: SyncTaskRun.Status,
        startedAt: LocalDateTime,
        result: AccountGenerateResult?,
        summary: () -> String,
    ) {
        try {
            syncTaskHistoryService.record(
                SyncTaskRunCommand(
                    taskKey = TASK_KEY,
                    taskName = TASK_NAME,
                    trigger = trigger,
                    status = status,
                    startedAt = startedAt,
                    finishedAt = LocalDateTime.now(),
                    processedCount = result?.createdCount,
                    failedPhotoCount = result?.failedCount,
                    summary = if (status == SyncTaskRun.Status.SUCCESS) summary() else null,
                    error = if (status == SyncTaskRun.Status.FAILED) summary() else null,
                ),
            )
        } catch (exception: RuntimeException) {
            logger.warn("写入账号生成执行历史失败", exception)
        }
    }

    private companion object {
        const val TASK_KEY = "account.generate"
        const val TASK_NAME = "车辆业主账号生成"
        const val DEPARTMENT_ID = 229L
        const val INITIAL_PASSWORD = "Fqjg20221022"
        const val STATUS_ENABLED = 1

        /** 账号生成数据源的进出记录回溯天数。 */
        const val ACTIVE_WINDOW_DAYS = 30L
        val SEPARATOR_PATTERN = Regex("[\\s·.。]")
        val logger = LoggerFactory.getLogger(SynAccountGenerateTask::class.java)
        val executionLock = ReentrantLock()
    }
}
