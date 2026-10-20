package top.foxball.cartask.service.impl

import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import top.foxball.cartask.audit.AuditAction
import top.foxball.cartask.audit.AuditCommand
import top.foxball.cartask.audit.AuditService
import top.foxball.cartask.entity.GatePerson
import top.foxball.cartask.entity.StoredFile
import top.foxball.cartask.repository.GatePersonRepository
import top.foxball.cartask.repository.StoredFileRepository
import top.foxball.cartask.scope.FaceRegistrationScopePolicy
import top.foxball.cartask.service.FaceRegistrationService
import top.foxball.cartask.service.FileService
import top.foxball.cartask.shared.GatePersonFields
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

@Service
class FaceRegistrationServiceImpl(
    private val gatePersonRepository: GatePersonRepository,
    private val storedFileRepository: StoredFileRepository,
    private val fileService: FileService,
    private val scopePolicy: FaceRegistrationScopePolicy,
    private val auditService: AuditService,
) : FaceRegistrationService {
    override fun list(query: FaceRegistrationService.ListQuery, page: Int, pageSize: Int): FaceRegistrationService.PageData {
        require(page >= 1) { "页码必须大于 0" }
        require(pageSize in 1..100) { "每页数量必须在 1 到 100 之间" }
        val keyword = query.keyword?.trim()?.takeIf { it.isNotEmpty() }
        val filtered = gatePersonRepository.findAll()
            .asSequence()
            .filter(scopePolicy::visible)
            .filter { person ->
                (keyword == null || person.code.contains(keyword, true) || person.name.contains(keyword, true) ||
                    person.phone.contains(keyword, true) || person.idCard.takeLast(4).contains(keyword, true)) &&
                    (query.departmentCode == null || person.departmentCode == query.departmentCode) &&
                    (query.departmentName == null || person.dept == query.departmentName) &&
                    (query.approveStatus == null || person.approveStatus == query.approveStatus) &&
                    (query.syncStatus == null || person.syncStatus == query.syncStatus)
            }
            .sortedByDescending { it.updatedAt }
            .toList()
        val from = ((page - 1) * pageSize).coerceAtMost(filtered.size)
        val to = (from + pageSize).coerceAtMost(filtered.size)
        return FaceRegistrationService.PageData(filtered.subList(from, to).map(::detail), filtered.size)
    }

    override fun get(id: Long): FaceRegistrationService.Detail = detail(scopePolicy.requireVisible(find(id)))

    override fun photo(id: Long): FaceRegistrationService.DownloadData {
        val person = scopePolicy.requireVisible(find(id))
        val file = storedFileRepository.findByBusinessTypeAndBusinessId(StoredFile.BUSINESS_GATE_PERSON, person.code)
            .maxByOrNull { it.createdAt }
            ?: throw IllegalArgumentException("人员照片不存在")
        val opened = fileService.openDownload(file.id)
        return FaceRegistrationService.DownloadData(opened.path, opened.originalFilename, opened.contentType, opened.sizeBytes)
    }

    @Transactional
    override fun register(
        name: String,
        phone: String,
        idCard: String,
        photo: MultipartFile,
        departmentCode: String?,
    ): FaceRegistrationService.Detail {
        validatePhoto(photo)
        val validatedName = GatePersonFields.requireName(name)
        val validatedPhone = GatePersonFields.requirePhone(phone)
        val validatedIdCard = GatePersonFields.requireIdCard(idCard)
        require(!gatePersonRepository.existsByIdCard(validatedIdCard)) { GatePersonFields.ID_CARD_EXISTS_MESSAGE }
        val department = scopePolicy.resolveDepartment(departmentCode)
        val uploaded = fileService.upload(photo)
        val now = LocalDateTime.now()
        val person = GatePerson().apply {
            code = generateCode()
            this.name = validatedName
            this.phone = validatedPhone
            this.idCard = validatedIdCard
            dept = department.name
            this.departmentCode = department.code
            face = uploaded.downloadUrl
            createTime = now
            updatedAt = now
            approveStatus = GatePerson.ApproveStatus.PENDING
            syncStatus = GatePerson.SyncStatus.NOT_SYNCED
        }
        val saved = gatePersonRepository.saveAndFlush(person)
        fileService.linkBusiness(uploaded.id, StoredFile.BUSINESS_GATE_PERSON, saved.code)
        auditService.record(
            AuditCommand(
                AuditAction.GATE_PERSON_CREATED,
                "gate_person",
                saved.id.toString(),
                targetSummary = mapOf("code" to saved.code, "department_code" to saved.departmentCode),
                afterData = mapOf("approve_status" to saved.approveStatus.value(), "sync_status" to saved.syncStatus.value()),
            ),
        )
        return detail(saved)
    }

    @Transactional
    override fun registerLegacy(
        code: String,
        department: String,
        name: String,
        phone: String,
        idCard: String,
        photo: MultipartFile,
    ): FaceRegistrationService.Detail {
        validatePhoto(photo)
        val validatedCode = GatePersonFields.requireCode(code)
        val validatedDepartment = GatePersonFields.requireDept(department)
        val validatedName = GatePersonFields.requireName(name)
        val validatedPhone = GatePersonFields.requirePhone(phone)
        val validatedIdCard = GatePersonFields.requireLegacyIdCard(idCard)
        require(!gatePersonRepository.existsByCode(validatedCode)) { GatePersonFields.CODE_EXISTS_MESSAGE }
        require(!gatePersonRepository.existsByIdCard(validatedIdCard)) { GatePersonFields.ID_CARD_EXISTS_MESSAGE }
        val departmentInfo = scopePolicy.resolveDepartmentText(validatedDepartment)
        val uploaded = fileService.upload(photo)
        val now = LocalDateTime.now()
        val person = GatePerson().apply {
            this.code = validatedCode
            this.dept = validatedDepartment
            departmentCode = departmentInfo.code
            this.name = validatedName
            this.phone = validatedPhone
            this.idCard = validatedIdCard
            face = uploaded.downloadUrl
            createTime = now
            updatedAt = now
        }
        val saved = gatePersonRepository.saveAndFlush(person)
        fileService.linkBusiness(uploaded.id, StoredFile.BUSINESS_GATE_PERSON, saved.code)
        auditService.record(AuditCommand(AuditAction.GATE_PERSON_CREATED, "gate_person", saved.id.toString(),
            targetSummary = mapOf("code" to saved.code, "department_code" to saved.departmentCode)))
        return detail(saved)
    }

    @Transactional
    override fun update(
        id: Long,
        name: String?,
        phone: String?,
        idCard: String?,
        photo: MultipartFile?,
    ): FaceRegistrationService.Detail {
        val person = scopePolicy.requireVisible(gatePersonRepository.findLockedById(id))
        photo?.let(::validatePhoto)
        val previousStatus = person.approveStatus
        val fields = mutableListOf<String>()
        var changed = false
        name?.let { GatePersonFields.requireName(it).also { value -> if (value != person.name) { person.name = value; changed = true; fields.add("name") } } }
        phone?.let { GatePersonFields.requirePhone(it).also { value -> if (value != person.phone) { person.phone = value; changed = true; fields.add("phone") } } }
        idCard?.let {
            val value = GatePersonFields.requireIdCard(it)
            if (value != person.idCard) {
                require(!gatePersonRepository.existsByIdCardAndIdNot(value, id)) { GatePersonFields.ID_CARD_EXISTS_MESSAGE }
                person.idCard = value
                changed = true
                fields.add("id_card")
            }
        }
        val uploaded = photo?.let { fileService.upload(it) }
        if (uploaded != null) {
            changed = true
            storedFileRepository.findByBusinessTypeAndBusinessId(StoredFile.BUSINESS_GATE_PERSON, person.code).forEach {
                it.businessType = StoredFile.BUSINESS_GATE_PERSON_ORPHAN
            }
            fields.add("photo")
            person.face = uploaded.downloadUrl
            fileService.linkBusiness(uploaded.id, StoredFile.BUSINESS_GATE_PERSON, person.code)
        }
        if (!changed) return detail(person)
        person.approveStatus = GatePerson.ApproveStatus.PENDING
        person.syncStatus = GatePerson.SyncStatus.NOT_SYNCED
        person.updatedAt = LocalDateTime.now()
        val saved = gatePersonRepository.saveAndFlush(person)
        auditService.record(
            AuditCommand(
                AuditAction.GATE_PERSON_UPDATED,
                "gate_person",
                saved.id.toString(),
                targetSummary = mapOf("code" to saved.code, "department_code" to saved.departmentCode),
                beforeData = mapOf("approve_status" to previousStatus.value()),
                afterData = mapOf("approve_status" to saved.approveStatus.value(), "sync_status" to saved.syncStatus.value(),
                    "changed_fields" to fields, "resubmitted" to (previousStatus != GatePerson.ApproveStatus.PENDING)),
            ),
        )
        return detail(saved)
    }

    @Transactional
    override fun updateLegacy(
        id: Long,
        code: String?,
        department: String?,
        name: String?,
        phone: String?,
        idCard: String?,
        photo: MultipartFile?,
    ): FaceRegistrationService.Detail {
        val current = scopePolicy.requireVisible(gatePersonRepository.findLockedById(id))
        photo?.let(::validatePhoto)
        val previousCode = current.code
        val previousDepartmentCode = current.departmentCode
        val previousStatus = current.approveStatus
        val changedFields = mutableListOf<String>()
        var changed = false
        code?.let {
            val validated = GatePersonFields.requireCode(it)
            require(!gatePersonRepository.existsByCodeAndIdNot(validated, id)) { GatePersonFields.CODE_EXISTS_MESSAGE }
            if (validated != current.code) {
                current.code = validated
                changed = true
                changedFields.add("code")
            }
        }
        department?.let {
            val validated = GatePersonFields.requireDept(it)
            val resolved = scopePolicy.resolveDepartmentText(validated)
            val departmentNameChanged = validated != current.dept
            val departmentCodeChanged = resolved.code != current.departmentCode
            if (departmentNameChanged || (current.departmentCode != null && departmentCodeChanged)) {
                current.dept = validated
                current.departmentCode = resolved.code
                changed = true
                changedFields.add("department")
            } else if (departmentCodeChanged) {
                // 补齐历史数据缺失的部门编码不应把已审核人员重新打回审核中。
                current.departmentCode = resolved.code
            }
        }
        name?.let {
            val validated = GatePersonFields.requireName(it)
            if (validated != current.name) {
                current.name = validated
                changed = true
                changedFields.add("name")
            }
        }
        phone?.let {
            val validated = GatePersonFields.requirePhone(it)
            if (validated != current.phone) {
                current.phone = validated
                changed = true
                changedFields.add("phone")
            }
        }
        idCard?.let {
            val validated = GatePersonFields.requireLegacyIdCard(it)
            if (validated != current.idCard) {
                require(!gatePersonRepository.existsByIdCardAndIdNot(validated, id)) { GatePersonFields.ID_CARD_EXISTS_MESSAGE }
                current.idCard = validated
                changed = true
                changedFields.add("id_card")
            }
        }
        val uploaded = photo?.let { fileService.upload(it) }
        if (uploaded != null) {
            changed = true
            changedFields.add("photo")
            storedFileRepository.findByBusinessTypeAndBusinessId(StoredFile.BUSINESS_GATE_PERSON, previousCode).forEach {
                it.businessType = StoredFile.BUSINESS_GATE_PERSON_ORPHAN
            }
            current.face = uploaded.downloadUrl
        }
        if (!changed) return detail(current)
        if (current.approveStatus != GatePerson.ApproveStatus.PENDING) {
            current.approveStatus = GatePerson.ApproveStatus.PENDING
            current.syncStatus = GatePerson.SyncStatus.NOT_SYNCED
        }
        current.updatedAt = LocalDateTime.now()
        val saved = gatePersonRepository.saveAndFlush(current)
        if (uploaded == null && (previousCode != saved.code || previousDepartmentCode != saved.departmentCode)) {
            fileService.relinkBusiness(StoredFile.BUSINESS_GATE_PERSON, previousCode, saved.code, saved.departmentCode)
        }
        uploaded?.let { fileService.linkBusiness(it.id, StoredFile.BUSINESS_GATE_PERSON, saved.code) }
        auditService.record(
            AuditCommand(
                AuditAction.GATE_PERSON_UPDATED,
                "gate_person",
                saved.id.toString(),
                targetSummary = mapOf("code" to saved.code, "department_code" to saved.departmentCode),
                beforeData = mapOf("approve_status" to previousStatus.value()),
                afterData = mapOf("approve_status" to saved.approveStatus.value(), "sync_status" to saved.syncStatus.value(), "changed_fields" to changedFields),
            ),
        )
        return detail(saved)
    }

    @Transactional
    override fun review(id: Long, approved: Boolean, reason: String?): FaceRegistrationService.Detail {
        val person = scopePolicy.requireVisible(gatePersonRepository.findLockedById(id))
        reviewOne(person, approved, reason)
        return detail(gatePersonRepository.saveAndFlush(person))
    }

    @Transactional
    override fun reviewBatch(ids: List<Long>, approved: Boolean, reason: String?): FaceRegistrationService.BatchReviewResult {
        require(ids.isNotEmpty()) { "审核列表不能为空" }
        require(ids.size <= 200) { "单次批量审核不能超过 200 人，请分批提交" }
        require(ids.distinct().size == ids.size) { "审核记录的 ID 不能重复" }
        if (!approved) require(!reason.isNullOrBlank()) { "拒绝时必须填写原因" }
        val byId = ids.sorted().mapNotNull { gatePersonRepository.findLockedById(it) }.associateBy { requireNotNull(it.id) }
        require(byId.size == ids.size) { "存在不存在的人员记录" }
        val persons = ids.map { scopePolicy.requireVisible(byId[it]) }
        persons.forEach { require(it.approveStatus == GatePerson.ApproveStatus.PENDING) { "该人员当前状态不允许审核，请编辑后重新提交" } }
        persons.forEach { reviewOne(it, approved, reason) }
        gatePersonRepository.saveAll(persons)
        return FaceRegistrationService.BatchReviewResult(reviewed = persons.size)
    }

    private fun reviewOne(person: GatePerson, approved: Boolean, reason: String?) {
        require(person.approveStatus == GatePerson.ApproveStatus.PENDING) { "该人员当前状态不允许审核，请编辑后重新提交" }
        if (!approved) require(!reason.isNullOrBlank()) { "拒绝时必须填写原因" }
        val before = person.approveStatus
        person.approveStatus = if (approved) GatePerson.ApproveStatus.APPROVED else GatePerson.ApproveStatus.REJECTED
        if (!approved) person.syncStatus = GatePerson.SyncStatus.NOT_SYNCED
        person.updatedAt = LocalDateTime.now()
        auditService.record(
            AuditCommand(
                AuditAction.GATE_PERSON_REVIEWED,
                "gate_person",
                person.id.toString(),
                reason = reason,
                targetSummary = mapOf("code" to person.code, "department_code" to person.departmentCode),
                beforeData = mapOf("review_status" to before.value()),
                afterData = mapOf("review_status" to person.approveStatus.value()),
            ),
        )
    }

    private fun find(id: Long): GatePerson? = gatePersonRepository.findById(id).orElse(null)

    private fun detail(person: GatePerson): FaceRegistrationService.Detail {
        val id = requireNotNull(person.id)
        val fileExists = storedFileRepository.findByBusinessTypeAndBusinessId(StoredFile.BUSINESS_GATE_PERSON, person.code).isNotEmpty()
        return FaceRegistrationService.Detail(
            id = id,
            code = person.code,
            name = person.name,
            phone = person.phone,
            idCard = person.idCard,
            dept = person.dept,
            departmentCode = person.departmentCode.orEmpty(),
            photoUrl = if (fileExists) "/api/face-registrations/$id/photo" else null,
            approveStatus = person.approveStatus,
            syncStatus = person.syncStatus,
            createTime = person.createTime,
            updatedAt = person.updatedAt,
        )
    }

    private fun validatePhoto(file: MultipartFile) {
        val digest = top.foxball.cartask.shared.FacePhoto.requireValid(file)
        require(!storedFileRepository.existsBySha256AndBusinessType(digest, StoredFile.BUSINESS_GATE_PERSON)) {
            "照片已关联其他登记记录，请使用独立照片"
        }
    }

    private fun generateCode(): String = "FP-${LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)}-${UUID.randomUUID().toString().replace("-", "").take(10)}"
}
