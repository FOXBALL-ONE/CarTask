package top.foxball.cartask.service.impl

import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode
import top.foxball.cartask.audit.AuditAction
import top.foxball.cartask.audit.AuditCommand
import top.foxball.cartask.audit.AuditService
import top.foxball.cartask.authentication.CurrentUserPrincipal
import top.foxball.cartask.entity.ParkingPlate
import top.foxball.cartask.entity.VehicleInoutRequest
import top.foxball.cartask.keytop.*
import top.foxball.cartask.repository.ParkingOwnerRepository
import top.foxball.cartask.repository.ParkingPlateRepository
import top.foxball.cartask.repository.VehicleInoutRequestRepository
import top.foxball.cartask.repository.ZoneTypeRepository
import top.foxball.cartask.scope.DataScope
import top.foxball.cartask.scope.ScopeGuard
import top.foxball.cartask.scope.ScopeQuerySupport
import top.foxball.cartask.service.VehicleInoutArchiveService
import top.foxball.cartask.service.VehicleInoutRequestService
import top.foxball.cartask.service.VehicleInoutRequestStateWriter
import top.foxball.cartask.shared.PlateNumbers
import top.foxball.cartask.shared.SerialNumbers
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class VehicleInoutRequestServiceImpl(
    private val repository: VehicleInoutRequestRepository,
    private val plateRepository: ParkingPlateRepository,
    private val ownerRepository: ParkingOwnerRepository,
    private val zoneTypeRepository: ZoneTypeRepository,
    private val scopeQuerySupport: ScopeQuerySupport,
    private val scopeGuard: ScopeGuard,
    private val keytopService: KeytopService,
    private val objectMapper: tools.jackson.databind.ObjectMapper,
    private val auditService: AuditService,
    private val stateWriter: VehicleInoutRequestStateWriter,
    private val archiveService: VehicleInoutArchiveService,
    private val keytopSyncRateLimiter: KeytopSyncRateLimiter? = null,
) : VehicleInoutRequestService {
    
    @Transactional(readOnly = true)
    
    
    override fun list(
        filter: VehicleInoutRequestService.ListFilter,
        page: Int,
        pageSize: Int
    ): Page<VehicleInoutRequest> {
        require(page >= 1) { "页码必须大于 0" }
        require(pageSize in 1..100) { "每页数量必须在 1 到 100 之间" }
        val scope = scopeGuard.currentScope()
        val filtered = scopeQuerySupport.visibleInScope(scope, repository.findAll())
            .filter { request ->
                (filter.keyword.isNullOrBlank() || matchesKeyword(request, filter.keyword)) &&
                        (filter.status == null || request.status == filter.status) &&
                        (filter.syncStatus == null || request.syncStatus == filter.syncStatus) &&
                        inDateRange(request.applyTime, filter.startDate, filter.endDate)
            }
            .sortedByDescending { it.applyTime }
        val from = ((page - 1) * pageSize).coerceAtMost(filtered.size)
        val to = (from + pageSize).coerceAtMost(filtered.size)
        return PageImpl(filtered.subList(from, to), PageRequest.of(page - 1, pageSize), filtered.size.toLong())
    }
    
    @Transactional(readOnly = true)
    
    
    override fun get(id: Long): VehicleInoutRequest = requireVisible(id)
    
    @Transactional
    
    
    override fun create(command: VehicleInoutRequestService.CreateCommand): VehicleInoutRequest {
        val scope = scopeGuard.currentScope()
        val archives = command.newOwner?.let { archiveService.createArchives(command.plate, it) }
        val plate = archives?.plate ?: requirePlateInScope(command.plate, scope)
        val cardName = requireNotNull(command.cardName.trim().takeIf(String::isNotEmpty)) { "月卡名称不能为空" }
        requireValidity(command.validFrom, command.validTo)
        require(!repository.existsByPlateNormalizedAndStatusIn(keytopPlateOf(plate.plate), OPEN_STATUSES)) {
            "该车牌已有待处理的进出申请"
        }
        
        val request = VehicleInoutRequest().apply {
            bindOwner(plate)
            this.cardName = cardName
            applyArea(command.areaCode)
            validFrom = command.validFrom
            validTo = command.validTo
            applyTime = LocalDateTime.now()
        }
        val saved = repository.save(request)
        auditService.record(
            AuditCommand(
                action = ACTION_CREATED,
                targetType = TARGET_TYPE,
                targetId = saved.id?.toString(),
                targetSummary = targetSummaryOf(saved) +
                        (archives?.let { mapOf("created_account" to it.accountUsername) } ?: emptyMap()),
                afterData = mapOf(
                    "review_status" to saved.status.value(),
                    "synchronized" to false,
                    "valid_from" to saved.validFrom,
                    "valid_to" to saved.validTo,
                ),
            ),
        )
        return saved
    }
    
    @Transactional
    
    
    override fun update(id: Long, command: VehicleInoutRequestService.UpdateCommand): VehicleInoutRequest {
        val scope = scopeGuard.currentScope()
        val request = requireVisible(id, scope)
        require(request.status == VehicleInoutRequest.Status.PENDING) { "只有待审核的申请可以编辑" }
        val before = lifecycleOf(request)
        var changed = false
        
        command.plate?.let { incoming ->
            val plate = requirePlateInScope(incoming, scope)
            if (keytopPlateOf(plate.plate) != keytopPlateOf(request.plate)) {
                require(
                    !repository.existsByPlateNormalizedAndStatusInAndIdNot(
                        keytopPlateOf(plate.plate),
                        OPEN_STATUSES,
                        id
                    )
                ) {
                    "该车牌已有待处理的进出申请"
                }
                request.bindOwner(plate)
                request.areaCode = null
                request.areaName = null
                changed = true
            }
        }
        command.cardName?.let { name ->
            val cardName = name.trim()
            require(cardName.isNotEmpty()) { "月卡名称不能为空" }
            if (cardName != request.cardName) {
                request.cardName = cardName
                changed = true
            }
        }
        if (command.areaCode != null) {
            val code = command.areaCode.trim().takeIf(String::isNotEmpty)
            if (code != request.areaCode) {
                request.areaCode = code
                request.areaName = code?.let(::requireAreaName)
                changed = true
            }
        }
        val validFrom = command.validFrom ?: request.validFrom
        val validTo = command.validTo ?: request.validTo
        requireValidity(validFrom, validTo)
        if (validFrom != request.validFrom || validTo != request.validTo) {
            request.validFrom = validFrom
            request.validTo = validTo
            changed = true
        }
        
        val saved = repository.save(request)
        if (changed) {
            auditService.record(
                AuditCommand(
                    action = ACTION_UPDATED,
                    targetType = TARGET_TYPE,
                    targetId = id.toString(),
                    targetSummary = targetSummaryOf(saved),
                    beforeData = before,
                    afterData = lifecycleOf(saved),
                ),
            )
        }
        return saved
    }
    
    @Transactional
    
    
    override fun review(id: Long, approved: Boolean, reason: String?): VehicleInoutRequest {
        val request = requireVisible(id)
        require(request.status == VehicleInoutRequest.Status.PENDING) { "该申请当前状态不允许审核" }
        val normalizedReason = reason?.trim()?.takeIf(String::isNotEmpty)
        if (!approved) require(normalizedReason != null) { "驳回原因不能为空" }
        
        val before = request.status
        request.status = if (approved) VehicleInoutRequest.Status.APPROVED else VehicleInoutRequest.Status.REJECTED
        request.reviewedBy = actorName()
        request.reviewedAt = LocalDateTime.now()
        request.reviewReason = normalizedReason
        val saved = repository.save(request)
        auditService.record(
            AuditCommand(
                action = ACTION_REVIEWED,
                targetType = TARGET_TYPE,
                targetId = id.toString(),
                reason = normalizedReason,
                targetSummary = targetSummaryOf(saved),
                beforeData = mapOf("review_status" to before.value()),
                afterData = mapOf("review_status" to saved.status.value(), "synchronized" to false),
            ),
        )
        return saved
    }
    
    @Transactional
    
    
    override fun cancel(id: Long, reason: String?): VehicleInoutRequest {
        val request = requireVisible(id)
        require(request.syncStatus != VehicleInoutRequest.SyncStatus.SYNCED) { "已下发的申请不能撤销" }
        require(request.status == VehicleInoutRequest.Status.PENDING || request.status == VehicleInoutRequest.Status.APPROVED) {
            "该申请当前状态不允许撤销"
        }
        val before = request.status
        request.status = VehicleInoutRequest.Status.CANCELLED
        request.reviewReason = reason?.trim()?.takeIf(String::isNotEmpty) ?: request.reviewReason
        val saved = repository.save(request)
        auditService.record(
            AuditCommand(
                action = ACTION_CANCELLED,
                targetType = TARGET_TYPE,
                targetId = id.toString(),
                reason = saved.reviewReason,
                targetSummary = targetSummaryOf(saved),
                beforeData = mapOf("review_status" to before.value()),
                afterData = mapOf("review_status" to saved.status.value()),
            ),
        )
        return saved
    }
    
    
    override fun synchronize(id: Long): VehicleInoutRequestService.SyncOutcome {
        val snapshot = keytopSyncRateLimiter?.snapshot()
        return if (snapshot == null) synchronizeInternal(id)
        else keytopSyncRateLimiter.withSnapshot(snapshot) { synchronizeInternal(id) }
    }

    private fun synchronizeInternal(id: Long): VehicleInoutRequestService.SyncOutcome {
        val request = requireVisible(id)
        require(request.status == VehicleInoutRequest.Status.APPROVED) { "只有已通过的申请可以下发给科拓" }
        require(request.syncStatus != VehicleInoutRequest.SyncStatus.SYNCED) { "该申请已下发给科拓，无需重复下发" }
        
        val userName = actorName()
        try {
            val existingCardId = resolveCardId(request)
            val cardId = existingCardId ?: issueMonthlyCard(request, userName)
            payMonthlyCard(request, userName, cardId, cardExisted = existingCardId != null)
            stateWriter.recordOutcome(
                id,
                synced = true,
                message = null,
                cardId = cardId,
                cardIssued = true,
                occurredAt = LocalDateTime.now()
            )
            auditOutcome(id, synced = true, message = null)
            return VehicleInoutRequestService.SyncOutcome(synced = true, message = "月卡已下发", cardId = cardId)
        } catch (exception: RuntimeException) {
            val message = exception.message?.take(2048) ?: exception.javaClass.simpleName
            logger.error("车辆进出申请下发科拓失败：requestId={}, plate={}", id, request.plate, exception)
            stateWriter.recordOutcome(
                id,
                synced = false,
                message = message,
                cardId = request.cardId,
                cardIssued = request.cardIssued,
                occurredAt = LocalDateTime.now(),
            )
            auditOutcome(id, synced = false, message = message)
            return VehicleInoutRequestService.SyncOutcome(synced = false, message = message, cardId = request.cardId)
        }
    }
    
    
    private fun auditOutcome(id: Long, synced: Boolean, message: String?) {
        runCatching { stateWriter.recordOutcomeAudit(id, synced, message) }
            .onFailure { logger.error("车辆进出申请下发审计写入失败：requestId={}", id, it) }
    }
    
    
    private fun resolveCardId(request: VehicleInoutRequest): Long? {
        request.cardId?.let { return it }
        val response = keytopService.getCarCardInfo(keytopPlateOf(request.plate))
        requireSuccess(response, "月卡查询")
        val cardId = extractCardId(response.data)
        require(!(request.cardIssued && cardId == null)) {
            "科拓此前已受理过新增月卡但未返回卡号，本次不再重复新增；请到科拓平台核对车牌 ${request.plate} 的月卡后重试"
        }
        return cardId
    }
    
    
    private fun issueMonthlyCard(request: VehicleInoutRequest, userName: String): Long {
        requireSuccess(
            keytopService.addCarCardNo(
                userId = KEYTOP_OPERATOR_ID,
                userName = userName,
                cardInfo = cardInfoOf(request),
                carLotList = carLotsOf(request),
                plateNoInfo = listOf(KeytopPlateNo(plateNo = keytopPlateOf(request.plate))),
            ),
            "月卡新增",
        )
        val requestId = requireNotNull(request.id)
        stateWriter.markCardIssued(requestId)
        val response = keytopService.getCarCardInfo(keytopPlateOf(request.plate))
        requireSuccess(response, "月卡查询")
        val cardId = requireNotNull(extractCardId(response.data)) {
            "科拓未返回月卡 ID，无法登记有效期，请到科拓平台确认后重试"
        }
        stateWriter.markCardIssued(requestId, cardId)
        return cardId
    }
    
    
    private fun payMonthlyCard(request: VehicleInoutRequest, userName: String, cardId: Long, cardExisted: Boolean) {
        if (cardExisted) {
            requireSuccess(
                keytopService.modifyCarCardNo(
                    userId = KEYTOP_OPERATOR_ID,
                    userName = userName,
                    cardInfo = cardInfoOf(request).copy(cardId = cardId),
                    carLotList = carLotsOf(request),
                    plateNoInfo = listOf(
                        KeytopPlateNo(
                            plateNo = keytopPlateOf(request.plate),
                            id = cardId,
                            plateState = PLATE_STATE_ENABLED
                        ),
                    ),
                ),
                "月卡修改",
            )
        }
        requireSuccess(
            keytopService.payCarCardFee(
                KeytopPayCarCardFeeRequest(
                    userId = KEYTOP_OPERATOR_ID,
                    userName = userName,
                    cardId = cardId,
                    carType = KEYTOP_MONTHLY_CAR_TYPE,
                    validFrom = request.validFrom,
                    validTo = request.validTo,
                ),
            ),
            "月卡缴费",
        )
    }
    
    
    private fun keytopPlateOf(plate: String): String = PlateNumbers.normalize(plate) ?: plate
    
    
    private fun cardInfoOf(request: VehicleInoutRequest): KeytopCardInfo = KeytopCardInfo(
        cardName = request.cardName,
        useName = request.owner,
        tel = request.phone,
        roomId = request.areaCode.orEmpty(),
        remak = "车辆进出申请登记 #${request.id}",
    )
    
    
    private fun carLotsOf(request: VehicleInoutRequest): List<KeytopCarLot> {
        val areaName = request.areaName ?: return emptyList()
        return listOf(
            KeytopCarLot(
                lotName = areaName,
                areaName = areaName,
                areaId = listOfNotNull(request.areaCode?.toIntOrNull()),
            ),
        )
    }
    
    
    private fun requireSuccess(response: KeytopResponse?, action: String) {
        require(response != null && response.code == SUCCESS_CODE) {
            "科拓${action}失败：${response?.code ?: "无响应"} ${response?.message.orEmpty()}".trim()
        }
    }
    
    
    private fun extractCardId(data: JsonNode?): Long? {
        if (data == null || data.isNull) return null
        val node = if (data.isString) objectMapper.readTree(data.asString()) else data
        val container = node.get("data")?.takeIf { !it.isNull } ?: node
        val card = container.get("cardInfo") ?: container.get("card_info") ?: container
        return FIRST_CARD_ID_FIELDS.asSequence()
            .mapNotNull { card.get(it) }
            .firstOrNull { !it.isNull && !it.isMissingNode }
            ?.asString()
            ?.toLongOrNull()
    }
    
    
    private fun VehicleInoutRequest.bindOwner(plate: ParkingPlate) {
        val owner = requireNotNull(ownerRepository.findById(plate.ownerId).orElse(null)) {
            "车牌对应的车主档案不存在"
        }
        this.plate = plate.plate
        plateId = requireNotNull(plate.id)
        ownerId = requireNotNull(owner.id)
        this.owner = owner.name
        phone = owner.phone
        dept = owner.dept
        departmentCode = owner.departmentCode ?: scopeQuerySupport.stampDepartmentCode(owner.dept, null)
    }
    
    
    private fun VehicleInoutRequest.applyArea(areaCode: String?) {
        this.areaCode = areaCode?.trim()?.takeIf(String::isNotEmpty)
        areaName = this.areaCode?.let(::requireAreaName)
    }
    
    
    private fun requireAreaName(areaCode: String): String =
        zoneTypeRepository.findByZoneCode(areaCode)?.zoneName
            ?: throw IllegalArgumentException("停车区域 $areaCode 不在停车区域字典中，请先在停车区域页面同步")
    
    
    private fun requireValidity(validFrom: LocalDateTime, validTo: LocalDateTime) {
        require(!validTo.isBefore(validFrom)) { "有效期结束时间不能早于开始时间" }
    }
    
    
    private fun inDateRange(value: LocalDateTime, startDate: LocalDate?, endDate: LocalDate?): Boolean =
        (startDate == null || !value.toLocalDate().isBefore(startDate)) &&
                (endDate == null || !value.toLocalDate().isAfter(endDate))
    
    
    private fun matchesKeyword(request: VehicleInoutRequest, keyword: String): Boolean =
        SerialNumbers.matches(request.id, keyword) ||
                request.plate.contains(keyword, true) ||
                request.owner.contains(keyword, true) ||
                request.phone.contains(keyword, true)
    
    
    private fun requirePlateInScope(rawPlate: String, scope: DataScope): ParkingPlate {
        val normalized = PlateNumbers.normalize(rawPlate)
        require(!normalized.isNullOrEmpty()) { "车牌号不能为空" }
        val plate = plateRepository.findAll()
            .firstOrNull { PlateNumbers.normalize(it.plate) == normalized }
            ?: throw IllegalArgumentException("车牌 $rawPlate 不在车牌档案中，请先在车牌信息里建档")
        return scopeGuard.requireVisiblePlate(plate, scope, "车牌不存在")
    }
    
    
    private fun requireVisible(id: Long): VehicleInoutRequest = requireVisible(id, scopeGuard.currentScope())
    
    
    private fun requireVisible(id: Long, scope: DataScope): VehicleInoutRequest =
        scopeGuard.requireVisibleRow(repository.findById(id).orElse(null), scope, "申请单不存在")
    
    
    private fun lifecycleOf(request: VehicleInoutRequest): Map<String, Any?> = mapOf(
        "review_status" to request.status.value(),
        "synchronized" to (request.syncStatus == VehicleInoutRequest.SyncStatus.SYNCED),
        "valid_from" to request.validFrom,
        "valid_to" to request.validTo,
    )
    
    
    private fun targetSummaryOf(request: VehicleInoutRequest): Map<String, Any?> = mapOf(
        "plate" to request.plate,
        "owner" to request.owner,
        "department_code" to request.departmentCode,
        "monthly_card" to request.cardName,
    )
    
    
    private fun actorName(): String =
        (SecurityContextHolder.getContext().authentication?.principal as? CurrentUserPrincipal)?.username
            ?: throw AccessDeniedException("缺少有效的操作人上下文")
    
    private companion object {
        const val TARGET_TYPE = "vehicle_inout_request"
        
        val ACTION_CREATED = AuditAction.VEHICLE_INOUT_REQUEST_CREATED
        val ACTION_UPDATED = AuditAction.VEHICLE_INOUT_REQUEST_UPDATED
        val ACTION_REVIEWED = AuditAction.VEHICLE_INOUT_REQUEST_REVIEWED
        val ACTION_CANCELLED = AuditAction.VEHICLE_INOUT_REQUEST_CANCELLED
        
        val logger = LoggerFactory.getLogger(VehicleInoutRequestServiceImpl::class.java)
        
        const val SUCCESS_CODE = 0
        
        
        const val KEYTOP_OPERATOR_ID = 1L
        
        
        const val KEYTOP_MONTHLY_CAR_TYPE = 1
        
        
        const val PLATE_STATE_ENABLED = 1
        
        val FIRST_CARD_ID_FIELDS = listOf("cardId", "card_id", "id")
        
        
        val OPEN_STATUSES = listOf(VehicleInoutRequest.Status.PENDING, VehicleInoutRequest.Status.APPROVED)
    }
}
