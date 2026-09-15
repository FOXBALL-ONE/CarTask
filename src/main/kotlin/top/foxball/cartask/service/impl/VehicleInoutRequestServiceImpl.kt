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
) : VehicleInoutRequestService {

    @Transactional(readOnly = true)
    /**
     * list：查询或读取相关数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param filter 参与本次处理的输入参数。
     * @param page 参与本次处理的输入参数。
     * @param pageSize 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    override fun list(
        filter: VehicleInoutRequestService.ListFilter,
        page: Int,
        pageSize: Int
    ): Page<VehicleInoutRequest> {
        require(page >= 1) { "页码必须大于 0" }
        require(pageSize in 1..100) { "每页数量必须在 1 到 100 之间" }
        val scope = scopeGuard.currentScope()
        // 与门禁人员一致：全表加载后在内存里按范围裁剪。
        // TODO 申请单量级上来后应把关键字与范围一并下推到 SQL。
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
    /**
     * get：查询或读取相关数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param id 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    override fun get(id: Long): VehicleInoutRequest = requireVisible(id)

    @Transactional
    /**
     * create：创建、保存或初始化相关数据。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param command 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    override fun create(command: VehicleInoutRequestService.CreateCommand): VehicleInoutRequest {
        val scope = scopeGuard.currentScope()
        // 带 newOwner 时车牌本来就不该存在，先建档再走正常流程；不带时车牌必须已建档。
        val archives = command.newOwner?.let { archiveService.createArchives(command.plate, it) }
        val plate = archives?.plate ?: requirePlateInScope(command.plate, scope)
        val cardName = requireNotNull(command.cardName.trim().takeIf(String::isNotEmpty)) { "月卡名称不能为空" }
        requireValidity(command.validFrom, command.validTo)
        // 同一车牌只能有一条未决申请：科拓的 AddCarCardNo 没有幂等键，重复登记会在车场里多出一张卡。
        // 按归一化车牌判定，否则把「京A12345」写成「京A·12345」就能为同一辆车再登记一条。
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
                // 顺带建了账号就一并记下：否则事后无从知道这条申请开通了谁的登录权限。
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
    /**
     * update：更新业务状态或修改相关配置。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param id 参与本次处理的输入参数。
     * @param command 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    override fun update(id: Long, command: VehicleInoutRequestService.UpdateCommand): VehicleInoutRequest {
        val scope = scopeGuard.currentScope()
        val request = requireVisible(id, scope)
        // 已决的申请单不再是草稿：驳回或撤销之后想改就重新登记一条，不能把旧结论悄悄复活。
        require(request.status == VehicleInoutRequest.Status.PENDING) { "只有待审核的申请可以编辑" }
        val before = lifecycleOf(request)
        var changed = false

        command.plate?.let { incoming ->
            val plate = requirePlateInScope(incoming, scope)
            // 按归一化车牌比较：把「京A12345」重填成「京A·12345」只是写法不同，不该重绑车主、清空区域。
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
                // 换车牌就换了车主，原区域是否还适用无法判断，一并清空由调用方重新选。
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
        // areaCode 为 null 表示本次不改；传空串表示清空区域（见 VehicleInoutRequestUpdateBody 的约定）。
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
    /**
     * review：执行当前模块中的业务操作。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param id 参与本次处理的输入参数。
     * @param approved 参与本次处理的输入参数。
     * @param reason 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    override fun review(id: Long, approved: Boolean, reason: String?): VehicleInoutRequest {
        val request = requireVisible(id)
        require(request.status == VehicleInoutRequest.Status.PENDING) { "该申请当前状态不允许审核" }
        val normalizedReason = reason?.trim()?.takeIf(String::isNotEmpty)
        // 驳回必须说明理由：申请人看不到别的上下文，只有这一句话能解释为什么被拒。
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
    /**
     * cancel：校验输入、状态或访问条件。
     *
     * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param id 参与本次处理的输入参数。
     * @param reason 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    override fun cancel(id: Long, reason: String?): VehicleInoutRequest {
        val request = requireVisible(id)
        // 已下发的申请单撤销后科拓侧仍持有月卡，本地标记撤销只会造成两边不一致，因此不允许。
        require(request.syncStatus != VehicleInoutRequest.SyncStatus.SYNCED) { "已下发的申请不能撤销" }
        // 只有还在流程里的申请可以撤销：已驳回的申请撤销它不改变任何事实，只会让状态更难看懂。
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

    /**
     * 下发月卡。
     *
     * 刻意**不加 @Transactional**：外部写（新增卡、缴费）与本地状态不可能一起回滚，
     * 而「已经发过卡」必须比任何一次 HTTP 调用活得久。所以每次状态变更都交给
     * [VehicleInoutRequestStateWriter] 用独立事务提交，失败与成功都留下可重试的依据。
     *
     * 先按车牌查平台是否已有月卡：**已有卡就只改有效期并缴费**，不再新增。
     * 科拓的 AddCarCardNo 没有幂等键，重复调用会在车场里多出一张卡，而系统没有撤销它的能力，
     * 所以「先查后写」加 [VehicleInoutRequest.cardIssued] 是这条链路唯一的幂等保证。
     */
    override fun synchronize(id: Long): VehicleInoutRequestService.SyncOutcome {
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
            // 失败也落库：一次网络超时不能让审批结论看起来像没发生过，否则重试没有依据。
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

    /**
     * 写审计，失败只记日志不改变下发结论：审计可以事后补，卡在车场里的状态补不了。
     */
    private fun auditOutcome(id: Long, synced: Boolean, message: String?) {
        runCatching { stateWriter.recordOutcomeAudit(id, synced, message) }
            .onFailure { logger.error("车辆进出申请下发审计写入失败：requestId={}", id, it) }
    }

    /**
     * 取本申请对应的科拓月卡 ID。
     *
     * [VehicleInoutRequest.cardIssued] 为真时只查不增：那表示科拓已经受理过新增，
     * 只是本地还没读回卡号；此时再去新增就会在车场里多出一张撤不掉的卡。
     * 真查不到则直接抛错交给人工核对，不自作主张改走新增分支。
     */
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

    /** 新增月卡并取回卡 ID。 */
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
        // 新增一成功就落独立事务：后面无论是读卡失败还是缴费失败，重试都不会再发一张。
        val requestId = requireNotNull(request.id)
        stateWriter.markCardIssued(requestId)
        // 新增成功后平台一般不回 cardId，只能按车牌再查一次；查不到就没法登记有效期，按失败处理。
        val response = keytopService.getCarCardInfo(keytopPlateOf(request.plate))
        requireSuccess(response, "月卡查询")
        val cardId = requireNotNull(extractCardId(response.data)) {
            "科拓未返回月卡 ID，无法登记有效期，请到科拓平台确认后重试"
        }
        // 卡号也一并落库：下次重试连查询都不用走，直接改卡 + 缴费。
        stateWriter.markCardIssued(requestId, cardId)
        return cardId
    }

    /** 登记或延长有效期。卡是平台上已有的就顺带改一次卡信息，避免车主换人后平台仍挂着旧姓名与旧电话。 */
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

    /**
     * 发给科拓的车牌号。
     *
     * 车牌档案是人工维护的，可能写成「京A·12345」，而科拓侧只认无间隔符的形式；两边格式不一致时
     * 查卡会查不到，于是「已有月卡」被判成「没有卡」，重试就多发一张。归一化规则与车主档案补建、
     * 数据范围解析共用同一套（见 PlateNumbers）。
     */
    private fun keytopPlateOf(plate: String): String = PlateNumbers.normalize(plate) ?: plate

    /**
     * cardInfoOf：执行当前模块中的业务操作。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param request 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    private fun cardInfoOf(request: VehicleInoutRequest): KeytopCardInfo = KeytopCardInfo(
        cardName = request.cardName,
        useName = request.owner,
        tel = request.phone,
        roomId = request.areaCode.orEmpty(),
        remak = "车辆进出申请登记 #${request.id}",
    )

    /**
     * carLotsOf：执行当前模块中的业务操作。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param request 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
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

    /** 科拓返回 code == 0 才算成功；其余一律当失败并把平台的 message 原样带出来。 */
    private fun requireSuccess(response: KeytopResponse?, action: String) {
        require(response != null && response.code == SUCCESS_CODE) {
            "科拓${action}失败：${response?.code ?: "无响应"} ${response?.message.orEmpty()}".trim()
        }
    }

    /** 从 GetCarCardInfo 的响应里取卡 ID；data 可能是 JSON 字符串也可能是对象，字段名有大小写两种写法。 */
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

    /**
     * 把车牌及其车主档案快照到申请单上。
     *
     * 车主姓名、电话与部门是下发给科拓的月卡字段，必须一起落库：只存车主 ID 的话，
     * 车主改电话或调部门会让一条已审批的申请在下发时取到与审批时不同的内容。
     */
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

    /**
     * applyArea：执行当前模块中的业务操作。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param areaCode 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    private fun VehicleInoutRequest.applyArea(areaCode: String?) {
        this.areaCode = areaCode?.trim()?.takeIf(String::isNotEmpty)
        areaName = this.areaCode?.let(::requireAreaName)
    }

    /**
     * 区域编码必须能在停车区域字典里找到。
     *
     * 解析不出来时不能放过：下发给科拓的 `carLotList` 完全由区域名拼出来，区域名缺失就发出
     * 一张绑不上车位的月卡——卡发了、费缴了，但车进不了场，问题要到车场现场才暴露。
     */
    private fun requireAreaName(areaCode: String): String =
        zoneTypeRepository.findByZoneCode(areaCode)?.zoneName
            ?: throw IllegalArgumentException("停车区域 $areaCode 不在停车区域字典中，请先在停车区域页面同步")

    /**
     * requireValidity：校验输入、状态或访问条件。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param validFrom 参与本次处理的输入参数。
     * @param validTo 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    private fun requireValidity(validFrom: LocalDateTime, validTo: LocalDateTime) {
        require(!validTo.isBefore(validFrom)) { "有效期结束时间不能早于开始时间" }
    }

    /**
     * inDateRange：执行当前模块中的业务操作。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param value 参与本次处理的输入参数。
     * @param startDate 参与本次处理的输入参数。
     * @param endDate 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    private fun inDateRange(value: LocalDateTime, startDate: LocalDate?, endDate: LocalDate?): Boolean =
        (startDate == null || !value.toLocalDate().isBefore(startDate)) &&
                (endDate == null || !value.toLocalDate().isAfter(endDate))

    /**
     * matchesKeyword：校验输入、状态或访问条件。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param request 参与本次处理的输入参数。
     * @param keyword 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    private fun matchesKeyword(request: VehicleInoutRequest, keyword: String): Boolean =
        SerialNumbers.matches(request.id, keyword) ||
                request.plate.contains(keyword, true) ||
                request.owner.contains(keyword, true) ||
                request.phone.contains(keyword, true)

    /**
     * 车牌必须已存在于车牌档案且在调用方范围内。
     *
     * 本页不新建车主与车牌：申请单的月卡信息（使用人、电话、部门）全部来自车主档案，
     * 让这里凭空造一条车主会绕开车主信息的建档与数据范围校验。
     */
    private fun requirePlateInScope(rawPlate: String, scope: DataScope): ParkingPlate {
        val normalized = PlateNumbers.normalize(rawPlate)
        require(!normalized.isNullOrEmpty()) { "车牌号不能为空" }
        // 车牌档案与申请单的间隔符格式可能不同，按归一化形式比对，避免「京A·12345」找不到「京A12345」。
        val plate = plateRepository.findAll()
            .firstOrNull { PlateNumbers.normalize(it.plate) == normalized }
            ?: throw IllegalArgumentException("车牌 $rawPlate 不在车牌档案中，请先在车牌信息里建档")
        return scopeGuard.requireVisiblePlate(plate, scope, "车牌不存在")
    }

    /**
     * requireVisible：校验输入、状态或访问条件。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param id 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    private fun requireVisible(id: Long): VehicleInoutRequest = requireVisible(id, scopeGuard.currentScope())

    /**
     * requireVisible：校验输入、状态或访问条件。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param id 参与本次处理的输入参数。
     * @param scope 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    private fun requireVisible(id: Long, scope: DataScope): VehicleInoutRequest =
        scopeGuard.requireVisibleRow(repository.findById(id).orElse(null), scope, "申请单不存在")

    /** 审批与下发状态的快照；审核流转变更的是它，业务字段的编辑不动它。 */
    private fun lifecycleOf(request: VehicleInoutRequest): Map<String, Any?> = mapOf(
        "review_status" to request.status.value(),
        "synchronized" to (request.syncStatus == VehicleInoutRequest.SyncStatus.SYNCED),
        "valid_from" to request.validFrom,
        "valid_to" to request.validTo,
    )

    /** 审计摘要字段必须都在 AuditServiceImpl.SAFE_KEYS 里，否则会被静默丢弃。 */
    private fun targetSummaryOf(request: VehicleInoutRequest): Map<String, Any?> = mapOf(
        "plate" to request.plate,
        "owner" to request.owner,
        "department_code" to request.departmentCode,
        "monthly_card" to request.cardName,
    )

    /**
     * actorName：执行当前模块中的业务操作。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
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

        /** 科拓要求传操作人 ID，本地账号与科拓用户体系无关，沿用参考实现的固定值。 */
        const val KEYTOP_OPERATOR_ID = 1L

        /** 月卡车（见 AccessRecord.displayVehicleTypeName：1-月卡车）。 */
        const val KEYTOP_MONTHLY_CAR_TYPE = 1

        /** 车牌状态 1 = 启用。 */
        const val PLATE_STATE_ENABLED = 1

        val FIRST_CARD_ID_FIELDS = listOf("cardId", "card_id", "id")

        /** 未决状态：还在流程里、可能被下发的申请。 */
        val OPEN_STATUSES = listOf(VehicleInoutRequest.Status.PENDING, VehicleInoutRequest.Status.APPROVED)
    }
}
