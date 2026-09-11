package top.foxball.cartask.task

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import top.foxball.cartask.audit.AuditRequestContext
import top.foxball.cartask.entity.CarMasterInfo
import top.foxball.cartask.entity.SyncTaskRun
import top.foxball.cartask.handler.AccountGenerateInProgressException
import top.foxball.cartask.repository.AccessRecordRepository
import top.foxball.cartask.service.CarMasterInfoService
import top.foxball.cartask.service.SyncTaskHistoryService
import top.foxball.cartask.service.SyncTaskRunCommand
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
 * 为车辆业主补建平台登录账号：以近 [ACTIVE_WINDOW_DAYS] 天内有进出记录的车牌为数据源
 * （数据库层面按车牌去重），关联车辆主档业主信息后创建账号，已有账号自动跳过。
 */
@Component
class SynAccountGenerateTask(
    private val userService: UserService,
    private val carMasterInfoService: CarMasterInfoService,
    private val accessRecordRepository: AccessRecordRepository,
    private val syncTaskHistoryService: SyncTaskHistoryService,
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
            .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
            .toSet()
        if (activePlates.isEmpty()) {
            logger.info("近 {} 天没有车辆进出记录，本次不生成账号", ACTIVE_WINDOW_DAYS)
            return AccountGenerateResult(0, 0, 0, LocalDateTime.now())
        }

        val masterByPlate = buildMasterByPlate(carMasterInfoService.getAllList(), activePlates)
        val phones = masterByPlate.values
            .mapNotNull { it.carMasterPhone?.trim()?.takeIf(String::isNotEmpty) }
            .toSet()
        val existingUsernames = userService.findExistingUsernames(phones)

        activePlates.forEach { plate ->
            val carMasterInfo = masterByPlate[plate] ?: run {
                skippedCount++
                logger.warn("跳过没有车辆主档的活跃车牌：{}", plate)
                return@forEach
            }
            val phone = carMasterInfo.carMasterPhone?.trim()
            if (phone.isNullOrEmpty()) {
                skippedCount++
                logger.warn("跳过无手机号的车辆业主，车辆主档 ID: {}", carMasterInfo.id)
                return@forEach
            }
            if (!processedPhones.add(phone)) {
                skippedCount++
                return@forEach
            }
            val nickName = carMasterInfo.carMasterName.trim()
            if (nickName.isEmpty()) {
                skippedCount++
                logger.warn("跳过无姓名的车辆业主，车辆主档 ID: {}", carMasterInfo.id)
                return@forEach
            }
            if (phone in existingUsernames) {
                skippedCount++
                return@forEach
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
                logger.error("为车辆业主创建平台账号失败，车辆主档 ID: {}, 手机号: {}", carMasterInfo.id, phone, exception)
            }
        }

        logger.info("车辆业主账号生成完成：创建 {} 个，跳过 {} 个，失败 {} 个", createdCount, skippedCount, failedCount)
        return AccountGenerateResult(createdCount, skippedCount, failedCount, LocalDateTime.now())
    }

    /** 按通行卡车牌建立车牌到车辆主档的映射；同一车牌被多个主档认领时保留第一个。 */
    private fun buildMasterByPlate(
        carMasterInfos: List<CarMasterInfo>,
        activePlates: Set<String>,
    ): Map<String, CarMasterInfo> {
        val masterByPlate = linkedMapOf<String, CarMasterInfo>()
        carMasterInfos.forEach { carMasterInfo ->
            carMasterInfo.cards.forEach { card ->
                val plate = card.carNumber?.trim()?.takeIf(String::isNotEmpty) ?: return@forEach
                if (plate in activePlates && plate !in masterByPlate) {
                    masterByPlate[plate] = carMasterInfo
                }
            }
        }
        return masterByPlate
    }

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

        /** 账号生成数据源的进出记录回溯天数。 */
        const val ACTIVE_WINDOW_DAYS = 30L
        val logger = LoggerFactory.getLogger(SynAccountGenerateTask::class.java)
        val executionLock = ReentrantLock()
    }
}
