package top.foxball.cartask.controller

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.transaction.Transactional
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import top.foxball.cartask.entity.ViolationRecord
import top.foxball.cartask.entity.ViolationSetting
import top.foxball.cartask.entity.ViolationSubject
import top.foxball.cartask.entity.type.ViolationType
import top.foxball.cartask.repository.ViolationRecordRepository
import top.foxball.cartask.repository.ViolationSettingRepository
import top.foxball.cartask.repository.ViolationSubjectRepository
import top.foxball.cartask.repository.ViolationTypeRepository
import top.foxball.cartask.scope.DataScopeResolver
import top.foxball.cartask.scope.ScopeQuerySupport
import top.foxball.cartask.shared.Response
import top.foxball.cartask.shared.ResponseBuilder
import java.time.LocalDateTime

/** 停车场违规记录、计分规则与处罚名单的管理接口。 */
@RestController
@RequestMapping("/api")
class ViolationManagementController(
    private val responseBuilder: ResponseBuilder,
    private val violationRecordRepository: ViolationRecordRepository,
    private val violationSubjectRepository: ViolationSubjectRepository,
    private val violationTypeRepository: ViolationTypeRepository,
    private val violationSettingRepository: ViolationSettingRepository,
    private val dataScopeResolver: DataScopeResolver,
    private val scopeQuerySupport: ScopeQuerySupport,
) {
    @GetMapping("/violations")
    @PreAuthorize("hasAuthority('violation:read')")
            /**
             * listViolations：查询或读取相关数据。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @param keyword 参与本次处理的输入参数。
             * @param typeId 参与本次处理的输入参数。
             * @param status 参与本次处理的输入参数。
             * @param startTime 参与本次处理的输入参数。
             * @param endTime 参与本次处理的输入参数。
             * @param page 参与本次处理的输入参数。
             * @param pageSize 参与本次处理的输入参数。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun listViolations(
        @RequestParam(required = false) keyword: String?,
        @RequestParam(name = "type_id", required = false) typeId: Long?,
        @RequestParam(required = false) status: String?,
        @RequestParam(name = "start_time", required = false) startTime: LocalDateTime?,
        @RequestParam(name = "end_time", required = false) endTime: LocalDateTime?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(name = "page_size", defaultValue = "10") pageSize: Int,
    ): ResponseEntity<Response> {
        require(page >= 1) { "页码必须大于 0" }
        require(pageSize in 1..100) { "每页数量必须在 1 到 100 之间" }
        val normalizedStatus = status?.takeIf(String::isNotBlank)?.let {
            runCatching { ViolationRecord.HandlingStatus.valueOf(it.uppercase()) }
                .getOrElse { throw IllegalArgumentException("不支持的处理状态") }
        }

        data class ViolationData(
            val id: Long,
            @param:JsonProperty("subject_id") val subjectId: Long,
            @param:JsonProperty("subject_name") val subjectName: String,
            @param:JsonProperty("subject_number") val subjectNumber: String,
            @param:JsonProperty("type_id") val typeId: Long,
            @param:JsonProperty("type_name") val typeName: String,
            val score: Int,
            val description: String?,
            val location: String?,
            val evidence: String?,
            val status: String,
            @param:JsonProperty("handler_name") val handlerName: String?,
            @param:JsonProperty("handling_remark") val handlingRemark: String?,
            @param:JsonProperty("violation_time") val violationTime: String,
            @param:JsonProperty("handled_at") val handledAt: String?,
            @param:JsonProperty("created_at") val createdAt: String,
        )

        data class SummaryData(
            val pending: Int,
            @param:JsonProperty("near_threshold") val nearThreshold: Int,
            @param:JsonProperty("active_penalties") val activePenalties: Int,
            val threshold: Int,
        )

        data class Response(val items: List<ViolationData>, val total: Int, val summary: SummaryData)

        val scope = dataScopeResolver.current()
        val all = violationRecordRepository.findAllWithViolationType()
        val filtered = all.filter { record ->
            // 范围谓词始终参与：违规记录本身没有部门字段，归属由主体承载。
            scopeQuerySupport.violationSubjectVisible(scope, record.subject) &&
                    (keyword.isNullOrBlank() || listOf(
                        record.subject.subjectNumber,
                        record.subject.subjectName,
                        record.violationType.violationName.orEmpty(),
                        record.violationLocation.orEmpty(),
                    ).any { it.contains(keyword, ignoreCase = true) }) &&
                    (typeId == null || record.violationType.id == typeId) &&
                    (normalizedStatus == null || record.handlingStatus == normalizedStatus) &&
                    (startTime == null || !record.violationTime.isBefore(startTime)) &&
                    (endTime == null || !record.violationTime.isAfter(endTime))
        }.sortedByDescending { it.violationTime }
        val from = ((page - 1) * pageSize).coerceAtMost(filtered.size)
        val to = (from + pageSize).coerceAtMost(filtered.size)
        val items = filtered.subList(from, to).map { record ->
            ViolationData(
                requireNotNull(record.id),
                requireNotNull(record.subject.id),
                record.subject.subjectName,
                record.subject.subjectNumber,
                requireNotNull(record.violationType.id),
                record.violationType.violationName ?: "未分类",
                record.violationFraction,
                record.violationContent,
                record.violationLocation,
                record.evidenceInfo,
                record.handlingStatus.name,
                record.handlerName,
                record.handlingRemark,
                record.violationTime.toString(),
                record.handledAt?.toString(),
                record.createdAt.toString(),
            )
        }
        val setting = setting()
        val scoredRecords = all.filter {
            it.handlingStatus in setOf(
                ViolationRecord.HandlingStatus.CONFIRMED,
                ViolationRecord.HandlingStatus.HANDLED
            )
        }
        val scores =
            scoredRecords.groupBy { it.subject.id }.mapValues { (_, records) -> records.sumOf { it.violationFraction } }
        val nearLine = (setting.scoreThreshold * 0.7).toInt().coerceAtLeast(1)
        val summary = SummaryData(
            all.count { it.handlingStatus == ViolationRecord.HandlingStatus.PENDING },
            scores.count { (_, score) -> score in nearLine until setting.scoreThreshold },
            scores.count { (subjectId, score) -> score >= setting.scoreThreshold && scoredRecords.first { it.subject.id == subjectId }.subject.status == ViolationSubject.Status.BANNED },
            setting.scoreThreshold,
        )
        val rs = Response(items, filtered.size, summary)
        return responseBuilder.ok().data(rs).build()
    }

    @PostMapping("/violations")
    @PreAuthorize("hasAuthority('violation:manage')")
    @Transactional
            /**
             * createViolation：创建、保存或初始化相关数据。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @param subjectNumber 参与本次处理的输入参数。
             * @param subjectName 参与本次处理的输入参数。
             * @param typeId 参与本次处理的输入参数。
             * @param violationTime 参与本次处理的输入参数。
             * @param location 参与本次处理的输入参数。
             * @param evidenceInfo 参与本次处理的输入参数。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun createViolation(
        @RequestParam(name = "subject_number") subjectNumber: String,
        @RequestParam(name = "subject_name") subjectName: String,
        @RequestParam(name = "type_id") typeId: Long,
        @RequestParam(name = "violation_time") violationTime: LocalDateTime,
        @RequestParam(required = false) location: String?,
        @RequestParam(name = "evidence_info", required = false) evidenceInfo: String?,
    ): ResponseEntity<Response> {
        require(subjectNumber.isNotBlank()) { "车牌号不能为空" }
        require(subjectName.isNotBlank()) { "车主名称不能为空" }
        val type = violationTypeRepository.findById(typeId).orElseThrow { IllegalArgumentException("违规类型不存在") }
        require(type.status == ViolationType.Status.Activity) { "违规类型已停用" }
        val normalizedSubjectNumber = subjectNumber.trim().uppercase()
        val subject =
            violationSubjectRepository.findBySubjectNumber(normalizedSubjectNumber) ?: violationSubjectRepository.save(
                ViolationSubject().apply {
                    subjectType = ViolationSubject.SubjectType.VEHICLE
                    this.subjectName = subjectName.trim()
                    this.subjectNumber = normalizedSubjectNumber
                },
            )
        val record = violationRecordRepository.save(
            ViolationRecord().apply {
                this.subject = subject
                violationType = type
                this.violationTime = violationTime
                violationLocation = location?.trim()?.takeIf(String::isNotEmpty)
                violationFraction = type.violationFraction ?: 0
                violationContent = type.violationContent
                this.evidenceInfo = evidenceInfo?.trim()?.takeIf(String::isNotEmpty)
            },
        )

        data class Response(val id: Long)

        val rs = Response(requireNotNull(record.id))
        return responseBuilder.created().message("违规记录已新增").data(rs).build()
    }

    @PutMapping("/violations/{id}/handling")
    @PreAuthorize("hasAuthority('violation:manage')")
    @Transactional
            /**
             * handleViolation：处理请求、事件或异常流程。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @param id 参与本次处理的输入参数。
             * @param status 参与本次处理的输入参数。
             * @param handlerName 参与本次处理的输入参数。
             * @param handlingRemark 参与本次处理的输入参数。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun handleViolation(
        @PathVariable id: Long,
        @RequestParam status: String,
        @RequestParam(name = "handler_name") handlerName: String,
        @RequestParam(name = "handling_remark", required = false) handlingRemark: String?,
    ): ResponseEntity<Response> {
        val nextStatus = runCatching { ViolationRecord.HandlingStatus.valueOf(status.uppercase()) }
            .getOrElse { throw IllegalArgumentException("不支持的处理状态") }
        require(nextStatus != ViolationRecord.HandlingStatus.PENDING) { "请选择处理结果" }
        require(handlerName.isNotBlank()) { "处理人不能为空" }
        val record = violationRecordRepository.findById(id).orElseThrow { IllegalArgumentException("违规记录不存在") }
        record.handlingStatus = nextStatus
        record.handlerName = handlerName.trim()
        record.handlingRemark = handlingRemark?.trim()?.takeIf(String::isNotEmpty)
        record.handledAt = LocalDateTime.now().withNano(0)
        violationRecordRepository.save(record)
        recalculateSubjectStatus(record.subject)
        data class Response(val id: Long, val status: String)

        val rs = Response(requireNotNull(record.id), record.handlingStatus.name)
        return responseBuilder.ok().message("处理结果已保存").data(rs).build()
    }

    @DeleteMapping("/violations/{id}")
    @PreAuthorize("hasAuthority('violation:manage')")
    @Transactional
            /**
             * deleteViolation：删除、清理或撤销相关数据。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @param id 参与本次处理的输入参数。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun deleteViolation(@PathVariable id: Long): ResponseEntity<Response> {
        val record = violationRecordRepository.findById(id).orElseThrow { IllegalArgumentException("违规记录不存在") }
        val subject = record.subject
        violationRecordRepository.delete(record)
        violationRecordRepository.flush()
        recalculateSubjectStatus(subject)
        data class Response(val id: Long)

        val rs = Response(id)
        return responseBuilder.ok().message("违规记录已删除").data(rs).build()
    }

    @GetMapping("/violation-types")
    @PreAuthorize("hasAuthority('violation:read')")
            /**
             * listViolationTypes：查询或读取相关数据。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun listViolationTypes(): ResponseEntity<Response> {
        data class TypeData(
            val id: Long,
            val name: String,
            val score: Int,
            val description: String?,
            val status: Int,
            @param:JsonProperty("sort_order") val sortOrder: Int,
            @param:JsonProperty("created_at") val createdAt: String,
        )

        data class Response(val items: List<TypeData>)

        val items =
            violationTypeRepository.findAll().sortedWith(compareBy<ViolationType> { it.orderNumber }.thenBy { it.id })
                .map {
                    TypeData(
                        requireNotNull(it.id),
                        it.violationName ?: "未命名规则",
                        it.violationFraction ?: 0,
                        it.violationContent,
                        if (it.status == ViolationType.Status.Activity) 1 else 0,
                        it.orderNumber,
                        it.createdAt.toString(),
                    )
                }
        val rs = Response(items)
        return responseBuilder.ok().data(rs).build()
    }

    @PostMapping("/violation-types")
    @PreAuthorize("hasAuthority('violation:manage')")
            /**
             * createViolationType：创建、保存或初始化相关数据。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @param name 参与本次处理的输入参数。
             * @param score 参与本次处理的输入参数。
             * @param description 参与本次处理的输入参数。
             * @param status 参与本次处理的输入参数。
             * @param sortOrder 参与本次处理的输入参数。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun createViolationType(
        @RequestParam name: String,
        @RequestParam score: Int,
        @RequestParam(required = false) description: String?,
        @RequestParam(defaultValue = "1") status: Int,
        @RequestParam(name = "sort_order", defaultValue = "0") sortOrder: Int,
    ): ResponseEntity<Response> {
        require(name.isNotBlank()) { "违规类型不能为空" }
        require(score in 1..100) { "违规分值必须在 1 到 100 之间" }
        require(status in 0..1) { "状态必须为 0 或 1" }
        require(violationTypeRepository.findFirstByViolationName(name.trim()) == null) { "违规类型已存在" }
        val type = violationTypeRepository.save(ViolationType().apply {
            violationName = name.trim()
            violationFraction = score
            violationContent = description?.trim()?.takeIf(String::isNotEmpty)
            this.status = if (status == 1) ViolationType.Status.Activity else ViolationType.Status.BANNED
            orderNumber = sortOrder
        })

        data class Response(val id: Long)

        val rs = Response(requireNotNull(type.id))
        return responseBuilder.created().message("计分规则已新增").data(rs).build()
    }

    @PutMapping("/violation-types/{id}")
    @PreAuthorize("hasAuthority('violation:manage')")
            /**
             * updateViolationType：更新业务状态或修改相关配置。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @param id 参与本次处理的输入参数。
             * @param name 参与本次处理的输入参数。
             * @param score 参与本次处理的输入参数。
             * @param description 参与本次处理的输入参数。
             * @param status 参与本次处理的输入参数。
             * @param sortOrder 参与本次处理的输入参数。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun updateViolationType(
        @PathVariable id: Long,
        @RequestParam name: String,
        @RequestParam score: Int,
        @RequestParam(required = false) description: String?,
        @RequestParam status: Int,
        @RequestParam(name = "sort_order") sortOrder: Int,
    ): ResponseEntity<Response> {
        require(name.isNotBlank()) { "违规类型不能为空" }
        require(score in 1..100) { "违规分值必须在 1 到 100 之间" }
        require(status in 0..1) { "状态必须为 0 或 1" }
        val duplicate = violationTypeRepository.findFirstByViolationName(name.trim())
        require(duplicate == null || duplicate.id == id) { "违规类型已存在" }
        val type = violationTypeRepository.findById(id).orElseThrow { IllegalArgumentException("违规类型不存在") }
        type.violationName = name.trim()
        type.violationFraction = score
        type.violationContent = description?.trim()?.takeIf(String::isNotEmpty)
        type.status = if (status == 1) ViolationType.Status.Activity else ViolationType.Status.BANNED
        type.orderNumber = sortOrder
        violationTypeRepository.save(type)
        data class Response(val id: Long)

        val rs = Response(requireNotNull(type.id))
        return responseBuilder.ok().message("计分规则已更新").data(rs).build()
    }

    @DeleteMapping("/violation-types/{id}")
    @PreAuthorize("hasAuthority('violation:manage')")
            /**
             * deleteViolationType：删除、清理或撤销相关数据。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @param id 参与本次处理的输入参数。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun deleteViolationType(@PathVariable id: Long): ResponseEntity<Response> {
        require(!violationRecordRepository.existsByViolationTypeId(id)) { "该规则已有违规记录，不能删除，可改为停用" }
        require(violationTypeRepository.existsById(id)) { "违规类型不存在" }
        violationTypeRepository.deleteById(id)
        data class Response(val id: Long)

        val rs = Response(id)
        return responseBuilder.ok().message("计分规则已删除").data(rs).build()
    }

    @GetMapping("/violation-settings")
    @PreAuthorize("hasAuthority('violation:read')")
    @Transactional
            /**
             * getViolationSetting：查询或读取相关数据。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun getViolationSetting(): ResponseEntity<Response> {
        data class Response(
            @param:JsonProperty("score_threshold") val scoreThreshold: Int,
            @param:JsonProperty("punishment_days") val punishmentDays: Int,
            @param:JsonProperty("updated_at") val updatedAt: String,
        )

        val setting = setting()
        val rs = Response(setting.scoreThreshold, setting.punishmentDays, setting.updatedAt.toString())
        return responseBuilder.ok().data(rs).build()
    }

    @PutMapping("/violation-settings")
    @PreAuthorize("hasAuthority('violation:manage')")
    @Transactional
            /**
             * updateViolationSetting：更新业务状态或修改相关配置。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @param scoreThreshold 参与本次处理的输入参数。
             * @param punishmentDays 参与本次处理的输入参数。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun updateViolationSetting(
        @RequestParam(name = "score_threshold") scoreThreshold: Int,
        @RequestParam(name = "punishment_days") punishmentDays: Int,
    ): ResponseEntity<Response> {
        require(scoreThreshold in 1..100) { "处罚分值必须在 1 到 100 之间" }
        require(punishmentDays in 1..365) { "处罚天数必须在 1 到 365 之间" }
        val setting = setting()
        setting.scoreThreshold = scoreThreshold
        setting.punishmentDays = punishmentDays
        violationSettingRepository.save(setting)
        violationRecordRepository.findAllWithViolationType().map { it.subject }.distinctBy { it.id }
            .forEach(::recalculateSubjectStatus)
        data class Response(
            @param:JsonProperty("score_threshold") val currentScoreThreshold: Int,
            @param:JsonProperty("punishment_days") val currentPunishmentDays: Int,
        )

        val rs = Response(setting.scoreThreshold, setting.punishmentDays)
        return responseBuilder.ok().message("处罚规则已更新").data(rs).build()
    }

    @GetMapping("/violation-penalties")
    @PreAuthorize("hasAuthority('violation:read')")
            /**
             * listViolationPenalties：查询或读取相关数据。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @param keyword 参与本次处理的输入参数。
             * @param minScore 参与本次处理的输入参数。
             * @param startTime 参与本次处理的输入参数。
             * @param endTime 参与本次处理的输入参数。
             * @param page 参与本次处理的输入参数。
             * @param pageSize 参与本次处理的输入参数。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun listViolationPenalties(
        @RequestParam(required = false) keyword: String?,
        @RequestParam(name = "min_score", required = false) minScore: Int?,
        @RequestParam(name = "start_time", required = false) startTime: LocalDateTime?,
        @RequestParam(name = "end_time", required = false) endTime: LocalDateTime?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(name = "page_size", defaultValue = "10") pageSize: Int,
    ): ResponseEntity<Response> {
        require(page >= 1) { "页码必须大于 0" }
        require(pageSize in 1..100) { "每页数量必须在 1 到 100 之间" }
        require(minScore == null || minScore >= 0) { "最低分值不能小于 0" }
        data class PenaltyData(
            @param:JsonProperty("subject_id") val subjectId: Long,
            @param:JsonProperty("subject_name") val subjectName: String,
            @param:JsonProperty("subject_number") val subjectNumber: String,
            @param:JsonProperty("total_score") val totalScore: Int,
            @param:JsonProperty("punishment_at") val punishmentAt: String,
            @param:JsonProperty("expires_at") val expiresAt: String,
            val status: String,
        )

        data class Response(val items: List<PenaltyData>, val total: Int)

        val setting = setting()
        val scope = dataScopeResolver.current()
        val penalties = violationRecordRepository.findAllWithViolationType()
            .filter { scopeQuerySupport.violationSubjectVisible(scope, it.subject) }
            .filter {
                it.handlingStatus in setOf(
                    ViolationRecord.HandlingStatus.CONFIRMED,
                    ViolationRecord.HandlingStatus.HANDLED
                )
            }
            .groupBy { it.subject.id }
            .mapNotNull { (_, subjectRecords) ->
                val ordered = subjectRecords.sortedBy { it.violationTime }
                val totalScore = ordered.sumOf { it.violationFraction }
                if (totalScore < setting.scoreThreshold) return@mapNotNull null
                var runningScore = 0
                val thresholdRecord = ordered.first { record ->
                    runningScore += record.violationFraction
                    runningScore >= setting.scoreThreshold
                }
                val subject = thresholdRecord.subject
                val punishmentAt = thresholdRecord.handledAt ?: thresholdRecord.violationTime
                val expiresAt = punishmentAt.plusDays(setting.punishmentDays.toLong())
                val penaltyStatus = when {
                    subject.status != ViolationSubject.Status.BANNED -> "RELEASED"
                    expiresAt.isBefore(LocalDateTime.now()) -> "EXPIRED"
                    else -> "ACTIVE"
                }
                PenaltyData(
                    requireNotNull(subject.id),
                    subject.subjectName,
                    subject.subjectNumber,
                    totalScore,
                    punishmentAt.toString(),
                    expiresAt.toString(),
                    penaltyStatus,
                )
            }
            .filter {
                (keyword.isNullOrBlank() || it.subjectNumber.contains(keyword, true) || it.subjectName.contains(
                    keyword,
                    true
                )) &&
                        (minScore == null || it.totalScore >= minScore) &&
                        (startTime == null || !LocalDateTime.parse(it.punishmentAt).isBefore(startTime)) &&
                        (endTime == null || !LocalDateTime.parse(it.punishmentAt).isAfter(endTime))
            }
            .sortedByDescending { it.punishmentAt }
        val from = ((page - 1) * pageSize).coerceAtMost(penalties.size)
        val to = (from + pageSize).coerceAtMost(penalties.size)
        val rs = Response(penalties.subList(from, to), penalties.size)
        return responseBuilder.ok().data(rs).build()
    }

    @PutMapping("/violation-penalties/{subjectId}/release")
    @PreAuthorize("hasAuthority('violation:manage')")
            /**
             * releaseViolationPenalty：执行当前模块中的业务操作。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @param subjectId 参与本次处理的输入参数。
             * @param releaseRemark 参与本次处理的输入参数。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun releaseViolationPenalty(
        @PathVariable subjectId: Long,
        @RequestParam(name = "release_remark", required = false) releaseRemark: String?,
    ): ResponseEntity<Response> {
        val subject =
            violationSubjectRepository.findById(subjectId).orElseThrow { IllegalArgumentException("处罚主体不存在") }
        subject.status = ViolationSubject.Status.Activity
        if (!releaseRemark.isNullOrBlank()) subject.remark = releaseRemark.trim()
        violationSubjectRepository.save(subject)
        data class Response(@param:JsonProperty("subject_id") val currentSubjectId: Long)

        val rs = Response(subjectId)
        return responseBuilder.ok().message("处罚已解除").data(rs).build()
    }

    @Transactional
    /**
     * setting：创建、保存或初始化相关数据。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    private fun setting(): ViolationSetting = violationSettingRepository.findFirstByOrderByIdAsc()
        ?: violationSettingRepository.save(ViolationSetting())

    /**
     * recalculateSubjectStatus：执行当前模块中的业务操作。
     *
     * 这是供当前类内部调用的辅助函数，负责完成既定业务规则下的参数处理、核心计算和结果返回。
     * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
     * @param subject 参与本次处理的输入参数。
     * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
     */
    private fun recalculateSubjectStatus(subject: ViolationSubject) {
        val score = violationRecordRepository.findAllWithViolationType()
            .filter {
                it.subject.id == subject.id &&
                        it.handlingStatus in setOf(
                    ViolationRecord.HandlingStatus.CONFIRMED,
                    ViolationRecord.HandlingStatus.HANDLED
                )
            }
            .sumOf { it.violationFraction }
        subject.status =
            if (score >= setting().scoreThreshold) ViolationSubject.Status.BANNED else ViolationSubject.Status.Activity
        violationSubjectRepository.save(subject)
    }
}
