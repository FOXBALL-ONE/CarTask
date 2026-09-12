package top.foxball.cartask.task

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import top.foxball.cartask.audit.AuditRequestContext
import top.foxball.cartask.entity.ParkingOwner
import top.foxball.cartask.entity.ParkingPlate
import top.foxball.cartask.entity.SyncTaskRun
import top.foxball.cartask.handler.AccountGenerateInProgressException
import top.foxball.cartask.keytop.KeytopService
import top.foxball.cartask.repository.AccessRecordRepository
import top.foxball.cartask.repository.ParkingOwnerRepository
import top.foxball.cartask.repository.ParkingPlateRepository
import top.foxball.cartask.service.SyncTaskHistoryService
import top.foxball.cartask.service.SyncTaskRunCommand
import top.foxball.cartask.service.SyncTaskProgressService
import top.foxball.cartask.shared.PlateNumbers
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.locks.ReentrantLock

data class OwnerArchiveResult(
    val createdOwnerCount: Int,
    val linkedPlateCount: Int,
    val skippedCount: Int,
    val executedAt: LocalDateTime,
)

/**
 * 从车辆进出记录里补建系统缺失的车主信息：以近 [ACTIVE_WINDOW_DAYS] 天内有进出记录的车牌为数据源
 * （数据库层面按车牌去重），对系统没有车主档案的车牌回查科拓 `getCardInfoByUser`，
 * 用返回的卡号、姓名、手机号新建车主档案（ParkingOwner），并把车牌登记到该车主名下
 * （新建或修正 ParkingPlate 的归属），使账号生成任务和后续执行直接命中档案。
 *
 * 车主按手机号判重，卡号作为次选依据：同一手机号对应多个车牌时只建一条车主档案。
 * 已经是「有效车牌档案 + 在营车主」的车牌直接跳过，本任务只做补建，不做更新。
 */
@Component
class SynOwnerArchiveTask(
    private val parkingPlateRepository: ParkingPlateRepository,
    private val parkingOwnerRepository: ParkingOwnerRepository,
    private val accessRecordRepository: AccessRecordRepository,
    private val syncTaskHistoryService: SyncTaskHistoryService,
    private val syncTaskProgressService: SyncTaskProgressService = SyncTaskProgressService(),
    private val keytopService: KeytopService? = null,
    private val objectMapper: ObjectMapper? = null,
) {
    @Scheduled(cron = "\${app.owner-archive-cron:0 45 2 * * *}", zone = "Asia/Shanghai")
    fun synOwnerArchive() {
        AuditRequestContext.withRun {
            try {
                generate(SyncTaskRun.Trigger.SCHEDULED)
            } catch (exception: AccountGenerateInProgressException) {
                logger.warn("车主档案补建仍在执行，本次定时任务跳过")
            } catch (exception: RuntimeException) {
                logger.error("车主档案补建失败", exception)
            }
        }
    }

    /** 手动执行一次车主档案补建。 */
    @Transactional(noRollbackFor = [RuntimeException::class])
    fun generate(): OwnerArchiveResult = generate(SyncTaskRun.Trigger.MANUAL)

    private fun generate(trigger: SyncTaskRun.Trigger): OwnerArchiveResult {
        if (!executionLock.tryLock()) {
            throw AccountGenerateInProgressException()
        }
        val startedAt = LocalDateTime.now()
        syncTaskProgressService.start(TASK_KEY, TASK_NAME, startedAt)
        try {
            val result = generateInternal(startedAt)
            recordHistory(trigger, SyncTaskRun.Status.SUCCESS, startedAt, result) {
                "新建车主 ${result.createdOwnerCount} 个，关联车牌 ${result.linkedPlateCount} 个，" +
                    "跳过 ${result.skippedCount} 个"
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

    private fun generateInternal(startedAt: LocalDateTime): OwnerArchiveResult {
        var createdOwnerCount = 0
        var linkedPlateCount = 0
        var skippedCount = 0

        val activePlates = accessRecordRepository
            .findDistinctCarNumbersSince(startedAt.minusDays(ACTIVE_WINDOW_DAYS))
            .mapNotNull(PlateNumbers::normalize)
            .toSet()
        syncTaskProgressService.update(TASK_KEY, 0, activePlates.size)
        if (activePlates.isEmpty()) {
            logger.info("近 {} 天没有车辆进出记录，本次不补建车主档案", ACTIVE_WINDOW_DAYS)
            return OwnerArchiveResult(0, 0, 0, LocalDateTime.now())
        }

        // 车牌档案按归一化车牌建索引；停用档案一并保留，避免为已停用车牌重复插入同一个车牌号。
        val plateByKey = parkingPlateRepository.findAll()
            .asSequence()
            .mapNotNull { plate -> PlateNumbers.normalize(plate.plate)?.let { it to plate } }
            .toMap(mutableMapOf())
        val owners = parkingOwnerRepository.findAll()
        val ownerById = owners.associateBy { requireNotNull(it.id) }.toMutableMap()
        // 补建车主前按手机号和卡号判重：手机号是账号登录名，卡号在库里还有唯一约束。
        val ownerByPhone = owners
            .mapNotNull { owner -> owner.phone.trim().takeIf(String::isNotEmpty)?.let { it to owner } }
            .toMap(mutableMapOf())
        val ownerByCardId = owners.associateBy { it.cardId }.toMutableMap()

        activePlates.forEachIndexed { index, plate ->
            syncTaskProgressService.update(TASK_KEY, index, activePlates.size)
            val archive = plateByKey[plate]
            if (archive != null && archive.status != STATUS_ENABLED) {
                skippedCount++
                logger.warn("跳过已停用的车牌档案：{}", plate)
                return@forEachIndexed
            }
            val archivedOwner = archive?.ownerId?.let(ownerById::get)
            if (archivedOwner != null && archivedOwner.status == STATUS_ENABLED) {
                skippedCount++
                return@forEachIndexed
            }

            val card = fetchCardOwner(plate) ?: run {
                skippedCount++
                logger.warn("科拓没有返回车牌 {} 的卡片信息，跳过补建车主", plate)
                return@forEachIndexed
            }
            var ownerCreated = false
            val owner = ownerByPhone[card.phone] ?: ownerByCardId[card.cardId] ?: run {
                val recordedAt = LocalDateTime.now()
                val created = parkingOwnerRepository.save(ParkingOwner().apply {
                    cardId = card.cardId
                    name = card.name
                    dept = SYNC_DEPARTMENT
                    phone = card.phone
                    createdAt = recordedAt
                    updatedAt = recordedAt
                })
                ownerById[requireNotNull(created.id)] = created
                ownerByCardId[created.cardId] = created
                ownerByPhone[created.phone] = created
                ownerCreated = true
                created
            }
            if (owner.status != STATUS_ENABLED) {
                skippedCount++
                logger.warn("跳过已停用车主：{}，车主 ID: {}", owner.name, owner.id)
                return@forEachIndexed
            }

            val now = LocalDateTime.now()
            val pending = archive ?: ParkingPlate().apply {
                this.plate = plate
                status = STATUS_ENABLED
                regDate = LocalDate.now()
                createdAt = now
            }
            pending.ownerId = requireNotNull(owner.id)
            pending.owner = owner.name
            pending.updatedAt = now
            plateByKey[plate] = parkingPlateRepository.save(pending)
            if (ownerCreated) createdOwnerCount++
            linkedPlateCount++
        }
        syncTaskProgressService.update(TASK_KEY, activePlates.size, activePlates.size)

        logger.info(
            "车主档案补建完成：新建车主 {} 个，关联车牌 {} 个，跳过 {} 个",
            createdOwnerCount,
            linkedPlateCount,
            skippedCount,
        )
        return OwnerArchiveResult(createdOwnerCount, linkedPlateCount, skippedCount, LocalDateTime.now())
    }

    /**
     * 回查科拓卡片信息接口，取出补建车主档案需要的卡号、姓名和手机号。
     * 无卡片车辆（例如临时车）平台不返回卡片数据，单个车牌查询失败也不影响其余车牌。
     */
    private fun fetchCardOwner(plate: String): KeytopOwnerCard? {
        val service = keytopService ?: return null
        val mapper = objectMapper ?: return null
        return try {
            val data = service.getCardInfoByUser(plate).data ?: return null
            val node = if (data.isTextual) mapper.readTree(data.asString()) else data
            val container = node.get("data")?.takeIf { !it.isNull } ?: node
            val card = container.get("cardInfo") ?: container.get("card_info") ?: container
            val name = firstText(card, "useName", "use_name", "userName", "user_name", "name") ?: return null
            val phone = firstText(card, "tel", "phone", "mobile") ?: return null
            val cardId = firstText(card, "cardName", "card_name", "cardNo", "card_no", "cardId", "card_id")
                ?: return null
            KeytopOwnerCard(cardId = cardId, name = name, phone = phone)
        } catch (exception: RuntimeException) {
            logger.error("查询车牌 {} 的科拓卡片信息失败", plate, exception)
            null
        }
    }

    private fun firstText(node: JsonNode, vararg names: String): String? = names.asSequence()
        .mapNotNull { node.get(it) }
        .firstOrNull { !it.isNull && !it.isMissingNode && it.asString().isNotBlank() }
        ?.asString()

    private fun recordHistory(
        trigger: SyncTaskRun.Trigger,
        status: SyncTaskRun.Status,
        startedAt: LocalDateTime,
        result: OwnerArchiveResult?,
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
                    processedCount = result?.createdOwnerCount,
                    failedPhotoCount = null,
                    summary = if (status == SyncTaskRun.Status.SUCCESS) summary() else null,
                    error = if (status == SyncTaskRun.Status.FAILED) summary() else null,
                ),
            )
        } catch (exception: RuntimeException) {
            logger.warn("写入车主档案补建执行历史失败", exception)
        }
    }

    /** 科拓卡片信息中用于补建车主档案的字段。 */
    private data class KeytopOwnerCard(val cardId: String, val name: String, val phone: String)

    private companion object {
        const val TASK_KEY = "owner.archive.generate"
        const val TASK_NAME = "车主档案补建"
        const val SYNC_DEPARTMENT = "同步车主"
        const val STATUS_ENABLED = 1

        /** 车主档案补建数据源的进出记录回溯天数。 */
        const val ACTIVE_WINDOW_DAYS = 30L
        val logger = LoggerFactory.getLogger(SynOwnerArchiveTask::class.java)
        val executionLock = ReentrantLock()
    }
}
