package top.foxball.cartask.task

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import top.foxball.cartask.service.CarMasterInfoService
import top.foxball.cartask.service.UserService

/** 为固定车位车辆使用人补建平台账户。 */
@Component
class SynAccountGenerateTask(
    private val userService: UserService,
    private val carMasterInfoService: CarMasterInfoService,
) {
    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Shanghai")
    fun synAccountGenerate() {
        if (!executionLock.tryLock()) {
            logger.warn("固定车位车辆使用人账户同步仍在执行，本次跳过")
            return
        }
        try {
            synAccountGenerateInternal()
        } finally {
            executionLock.unlock()
        }
    }

    private fun synAccountGenerateInternal() {
        var createdCount = 0
        var skippedCount = 0
        var failedCount = 0
        val processedPhones = mutableSetOf<String>()

        val carMasterInfos = carMasterInfoService.getAllList()
        val phones = carMasterInfos.mapNotNull { it.carMasterPhone?.trim()?.takeIf(String::isNotEmpty) }.toSet()
        val existingUsernames = try {
            userService.findExistingUsernames(phones)
        } catch (exception: RuntimeException) {
            logger.error("查询已存在平台账户失败，本次同步终止", exception)
            return
        }

        carMasterInfos.forEach { carMasterInfo ->
            val phone = carMasterInfo.carMasterPhone?.trim()
            if (phone.isNullOrEmpty()) {
                skippedCount++
                logger.warn("跳过无手机号的车辆使用人，车辆主档 ID: {}", carMasterInfo.id)
                return@forEach
            }
            if (!processedPhones.add(phone)) {
                skippedCount++
                return@forEach
            }
            val nickName = carMasterInfo.carMasterName.trim()
            if (nickName.isEmpty()) {
                skippedCount++
                logger.warn("跳过无姓名的车辆使用人，车辆主档 ID: {}", carMasterInfo.id)
                return@forEach
            }

            try {
                if (phone in existingUsernames) {
                    skippedCount++
                    return@forEach
                }
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
                logger.error(
                    "为车辆使用人创建平台账户失败，车辆主档 ID: {}, 手机号: {}",
                    carMasterInfo.id,
                    phone,
                    exception,
                )
            }
        }

        logger.info("固定车位车辆使用人账户同步完成：创建 {} 个，跳过 {} 个，失败 {} 个", createdCount, skippedCount, failedCount)
    }

    private companion object {
        const val DEPARTMENT_ID = 229L
        const val INITIAL_PASSWORD = "Fqjg20221022"
        val logger = LoggerFactory.getLogger(SynAccountGenerateTask::class.java)
        val executionLock = java.util.concurrent.locks.ReentrantLock()
    }
}
