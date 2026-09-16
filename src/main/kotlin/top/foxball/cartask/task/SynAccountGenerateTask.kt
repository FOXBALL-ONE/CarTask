package top.foxball.cartask.task

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import top.foxball.cartask.audit.AuditRequestContext
import top.foxball.cartask.entity.Department
import top.foxball.cartask.entity.SyncTaskRun
import top.foxball.cartask.handler.AccountGenerateInProgressException
import top.foxball.cartask.repository.AccessRecordRepository
import top.foxball.cartask.repository.DepartmentRepository
import top.foxball.cartask.repository.ParkingOwnerRepository
import top.foxball.cartask.repository.ParkingPlateRepository
import top.foxball.cartask.service.SyncTaskHistoryService
import top.foxball.cartask.service.SyncTaskProgressService
import top.foxball.cartask.service.SyncTaskRunCommand
import top.foxball.cartask.service.UserService
import top.foxball.cartask.shared.InitialCredentials
import top.foxball.cartask.shared.PlateNumbers
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.concurrent.locks.ReentrantLock

data class AccountGenerateResult(
    val createdCount: Int,
    val skippedCount: Int,
    val failedCount: Int,
    val executedAt: LocalDateTime,
)


@Component
class SynAccountGenerateTask(
    private val userService: UserService,
    private val parkingPlateRepository: ParkingPlateRepository,
    private val parkingOwnerRepository: ParkingOwnerRepository,
    private val accessRecordRepository: AccessRecordRepository,
    private val departmentRepository: DepartmentRepository,
    private val syncTaskHistoryService: SyncTaskHistoryService,
    private val syncTaskProgressService: SyncTaskProgressService = SyncTaskProgressService(),
) {
    
    
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
            .mapNotNull(PlateNumbers::normalize)
            .toSet()
        syncTaskProgressService.update(TASK_KEY, 0, activePlates.size)
        if (activePlates.isEmpty()) {
            logger.info("近 {} 天没有车辆进出记录，本次不生成账号", ACTIVE_WINDOW_DAYS)
            return AccountGenerateResult(0, 0, 0, LocalDateTime.now())
        }
        
        val plateByKey = parkingPlateRepository.findAll()
            .asSequence()
            .filter { it.status == STATUS_ENABLED }
            .mapNotNull { plate -> PlateNumbers.normalize(plate.plate)?.let { it to plate } }
            .toMap()
        val ownerById = parkingOwnerRepository
            .findAllById(plateByKey.values.map { it.ownerId }.toSet())
            .filter { it.status == STATUS_ENABLED }
            .associateBy { requireNotNull(it.id) }
            .toMutableMap()
        val existingUsernames = userService.findExistingUsernames(
            ownerById.values.mapNotNull { it.phone.trim().takeIf(String::isNotEmpty) }.toSet(),
        )
        val departmentByName = departmentRepository.findAll()
            .associateByTo(mutableMapOf()) { it.name.trim() }
        
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
                        email = InitialCredentials.placeholderEmail(phone),
                        credential = InitialCredentials.PASSWORD,
                        phone = phone,
                        departmentId = ensureDepartment(owner.dept, departmentByName)?.id,
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
    
    
    private fun ensureDepartment(name: String, cache: MutableMap<String, Department>): Department? {
        val departmentName = name.trim().takeIf(String::isNotEmpty) ?: return null
        return cache.getOrPut(departmentName) {
            val code = MessageDigest.getInstance("SHA-256")
                .digest(departmentName.toByteArray(Charsets.UTF_8))
                .take(DIGEST_BYTES)
                .joinToString("") { "%02X".format(it) }
            departmentRepository.save(
                Department().apply {
                    this.name = departmentName
                    departmentNumber = "$DEPARTMENT_CODE_PREFIX$code"
                    sortOrder = 0
                    status = STATUS_ENABLED
                },
            )
        }
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
    
    companion object {
        const val TASK_KEY = "account.generate"
        const val TASK_NAME = "车辆业主账号生成"
        const val STATUS_ENABLED = 1
        
        
        const val DEPARTMENT_CODE_PREFIX = "AUTO-"
        
        
        const val DIGEST_BYTES = 5
        
        
        const val ACTIVE_WINDOW_DAYS = 30L
        val logger = LoggerFactory.getLogger(SynAccountGenerateTask::class.java)
        val executionLock = ReentrantLock()
    }
}
