package top.foxball.cartask.scope

import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Component
import top.foxball.cartask.entity.*
import top.foxball.cartask.repository.GatePersonRepository
import top.foxball.cartask.repository.ParkingOwnerRepository
import top.foxball.cartask.repository.ParkingPlateRepository
import top.foxball.cartask.shared.PlateNumbers

/**
 * 各业务实体的范围可见性。
 *
 * 只有车辆进出记录下推到 SQL（它的列表是数据库分页并直接返回总数的，事后过滤会让总数失真）；
 * 其余列表目前仍是全表加载后内存过滤，所以这里既提供 SQL 谓词也提供逐行判定。
 *
 * TODO 车主、车位、车牌、门禁人员、违规、仪表盘聚合仍是内存过滤，数据量上来后应同样下沉到 SQL。
 */
@Component
class ScopeQuerySupport(
    private val departmentLinkResolver: DepartmentLinkResolver,
    private val parkingOwnerRepository: ParkingOwnerRepository,
    private val parkingPlateRepository: ParkingPlateRepository,
    private val gatePersonRepository: GatePersonRepository,
) {
    /** 某车主在部门维度下是否可见。 */
    fun ownerVisibleInDepartments(departments: DepartmentSnapshot, owner: ParkingOwner, scope: DataScope): Boolean {
        val code = owner.departmentCode ?: departments.toCode(owner.dept)
        return code != null && code in scope.departmentCodes
    }

    /**
     * 本人范围下归属该账号的车主档案。
     *
     * 显式指定的优先；手机号命中但**已被显式指定给别的账号**的车主不算——共用号码、号码被
     * 回收的场景下，手机号巧合不能当成归属。
     */
    fun ownersOf(userId: Long, phone: String?): List<ParkingOwner> {
        val linked = parkingOwnerRepository.findByLinkedUserId(userId)
        val byPhone = phone
            ?.let { parkingOwnerRepository.findByPhone(it).filter { owner -> owner.linkedUserId == null } }
            ?: emptyList()
        return (linked + byPhone).distinctBy { it.id }
    }

    /** 范围内可见的车主档案。 */
    fun ownersInScope(scope: DataScope): List<ParkingOwner> = when (scope.kind) {
        ScopeKind.ALL -> parkingOwnerRepository.findAll()
        ScopeKind.DEPARTMENTS -> {
            val departments = departmentLinkResolver.snapshot()
            parkingOwnerRepository.findAll().filter { ownerVisibleInDepartments(departments, it, scope) }
        }

        ScopeKind.SELF -> ownersOf(requireNotNull(scope.userId), scope.phone)
    }

    /** 范围内可见的车主 ID；车牌按 ownerId 关联。 */
    fun ownerIdsInScope(scope: DataScope): Set<Long> =
        ownersInScope(scope).mapNotNull { it.id }.toSet()

    /** 范围内可见的车主卡号；车位按卡号（而不是姓名）关联。 */
    fun ownerCardIdsInScope(scope: DataScope): Set<String> =
        ownersInScope(scope).map { it.cardId }.toSet()

    /** 范围内可见的车牌号（已归一化）。 */
    fun plateNumbersInScope(scope: DataScope): Set<String> {
        if (scope.kind == ScopeKind.SELF) return scope.carNumbers
        val ownerIds = ownerIdsInScope(scope)
        if (ownerIds.isEmpty()) return emptySet()
        return PlateNumbers.normalizeAll(parkingPlateRepository.findByOwnerIdIn(ownerIds).map { it.plate })
    }

    /** 车牌是否可见：显式归属该车牌的账号优先，其次按车主归属。 */
    fun plateVisible(visibleOwnerIds: Set<Long>?, scopeUserId: Long?, plate: ParkingPlate): Boolean =
        visibleOwnerIds == null ||
                plate.ownerId in visibleOwnerIds ||
                (scopeUserId != null && plate.linkedUserId == scopeUserId)

    /** 把部门维度范围应用到已加载的实体列表（车主、门禁人员这类只有部门归属的主数据）。 */
    fun <T : DepartmentScoped> visibleInScope(scope: DataScope, rows: List<T>): List<T> =
        rows.scoped(scope, departmentLinkResolver.snapshot())

    /**
     * 写入时落稳定部门编码。
     *
     * 解析不出来时**保留原值**：范围判定是 fail closed 的，如果因为部门名一时写错就把编码清空，
     * 这条数据会立刻对所有受限角色不可见。维持既有归属比清空更安全。
     */
    fun stampDepartmentCode(freeText: String?, current: String?): String? =
        departmentLinkResolver.snapshot().toCode(freeText) ?: current

    /**
     * 写入时落车主卡号。
     *
     * 同名车主不唯一时保留原值：重名本就无法判定归属，猜一个等于把车位划到别的部门名下。
     * TODO 现在是全量读车主后在内存里找，车主表变大后应改为按姓名查询。
     */
    fun stampOwnerCode(ownerName: String?, current: String?): String? {
        if (ownerName.isNullOrBlank()) return current
        return parkingOwnerRepository.findAll().filter { it.name == ownerName }.singleOrNull()?.cardId ?: current
    }

    /**
     * 车位是否可见。
     *
     * 严格按车主卡号判定，**不回退到车主姓名**：姓名会重名，按姓名兜底会把别的部门的车位
     * 算进来。因此历史车位在 `owner_code` 回填完成前对受限角色一律不可见（fail closed）。
     *
     * [visibleOwnerCodes] 为 null 表示不受限；由调用方在进入逐行过滤前算好，避免每行重算一次。
     */
    fun spotVisible(visibleOwnerCodes: Set<String>?, ownerCode: String?): Boolean =
        visibleOwnerCodes == null || (ownerCode != null && ownerCode in visibleOwnerCodes)

    /**
     * 车辆进出记录的范围谓词。
     *
     * 部门维度是**「或」关系**而不是只看部门名快照：[AccessRecord.departmentName] 是同步时写下的
     * 历史快照，只按它过滤会让部门改名后该部门的历史记录整体消失——而这正是这个快照字段当初要避免的。
     * 因此同时按「当前归属该部门的车牌」匹配，这一支跟随车主现属部门，天然抗改名。
     */
    fun accessRecordSpec(scope: DataScope): Specification<AccessRecord> = Specification { root, _, cb ->
        when (scope.kind) {
            ScopeKind.ALL -> cb.conjunction()

            ScopeKind.SELF -> if (scope.carNumbers.isEmpty()) {
                // 没有任何关联车牌：匹配不到任何行，返回恒假而不是恒真。
                cb.disjunction()
            } else {
                cb.and(root.get<String>("carNumberNormalized").`in`(scope.carNumbers))
            }

            ScopeKind.DEPARTMENTS -> {
                val plates = plateNumbersInScope(scope)
                val names = scope.departmentNames
                when {
                    plates.isEmpty() && names.isEmpty() -> cb.disjunction()
                    plates.isEmpty() -> cb.and(root.get<String>("departmentName").`in`(names))
                    names.isEmpty() -> cb.and(root.get<String>("carNumberNormalized").`in`(plates))
                    else -> cb.or(
                        root.get<String>("carNumberNormalized").`in`(plates),
                        root.get<String>("departmentName").`in`(names),
                    )
                }
            }
        }
    }

    /**
     * 文件是否可见。
     *
     * 文件表原先没有任何归属字段，任何 `file:read` 持有者只要知道 UUID 就能下载任意附件，
     * 进出抓拍图片也一样。现在按三条路径判定：上传者本人、归属部门，或经业务对象关联到本人的
     * 车牌 / 门禁人员编号。解析不出归属的文件对受限角色不可见（fail closed）。
     */
    fun fileVisible(scope: DataScope, file: StoredFile): Boolean = when (scope.kind) {
        ScopeKind.ALL -> true

        // 部门维度优先按显式落标的部门，其次按业务对象反查：同步任务写入的抓拍图片没有登录
        // 主体、拿不到"上传者的工作部门"，但它的车牌能反查到当前车主所在部门，而且换车主、
        // 改部门名之后依然有效。
        ScopeKind.DEPARTMENTS ->
            (file.departmentCode != null && file.departmentCode in scope.departmentCodes) ||
                    (
                            file.businessType == StoredFile.BUSINESS_VEHICLE_PLATE &&
                                    file.businessId != null && file.businessId in plateNumbersInScope(scope)
                            ) ||
                    (
                            file.businessType == StoredFile.BUSINESS_GATE_PERSON &&
                                    file.businessId != null && file.businessId in gatePersonCodesInScope(scope)
                            )

        ScopeKind.SELF ->
            (scope.userId != null && file.uploadedByUserId == scope.userId) ||
                    (
                            file.businessType == StoredFile.BUSINESS_VEHICLE_PLATE &&
                                    file.businessId != null && file.businessId in scope.carNumbers
                            ) ||
                    (
                            file.businessType == StoredFile.BUSINESS_GATE_PERSON &&
                                    file.businessId != null && file.businessId in scope.gatePersonCodes
                            )
    }

    /** 部门范围内可见的门禁人员编号；门禁图片按它判定归属。 */
    fun gatePersonCodesInScope(scope: DataScope): Set<String> = when (scope.kind) {
        ScopeKind.ALL -> emptySet()
        ScopeKind.SELF -> scope.gatePersonCodes
        // TODO 现在是全量读门禁人员后内存过滤，量级上来后应改为带部门条件的查询。
        ScopeKind.DEPARTMENTS -> gatePersonRepository.findAll()
            .filter { it.departmentCode != null && it.departmentCode in scope.departmentCodes }
            .map { it.code }
            .toSet()
    }

    /**
     * 违规主体是否可见。
     *
     * 违规记录本身没有部门字段，归属落在主体上（见 [ViolationSubject.departmentCode]）。
     * 解析不出归属的主体对受限角色不可见。
     */
    fun violationSubjectVisible(scope: DataScope, subject: ViolationSubject): Boolean = when (scope.kind) {
        ScopeKind.ALL -> true

        ScopeKind.DEPARTMENTS ->
            subject.departmentCode != null && subject.departmentCode in scope.departmentCodes

        ScopeKind.SELF ->
            (scope.userId != null && subject.linkedUserId == scope.userId) ||
                    (
                            subject.subjectType == ViolationSubject.SubjectType.VEHICLE &&
                                    PlateNumbers.normalize(subject.subjectNumber) in scope.carNumbers
                            ) ||
                    (
                            subject.subjectType == ViolationSubject.SubjectType.PERSON &&
                                    subject.subjectNumber in scope.gatePersonCodes
                            )
    }

    /**
     * 人员进出记录的范围过滤。
     *
     * 本表没有手机号，本人维度只能靠卡号或姓名匹配门禁人员；姓名会重名，所以写入时若能按卡号
     * 匹配到门禁人员，应把归属账号落到 [PersonAccessRecord.linkedUserId] 上，那条路径才可靠。
     */
    fun personRecordsInScope(scope: DataScope, rows: List<PersonAccessRecord>): List<PersonAccessRecord> {
        if (scope.kind == ScopeKind.ALL) return rows
        // 部门快照在这里取一次，避免逐行查库。
        val departments = if (scope.kind == ScopeKind.DEPARTMENTS) departmentLinkResolver.snapshot() else null
        return rows.filter { record ->
            when (scope.kind) {
                ScopeKind.ALL -> true

                ScopeKind.SELF ->
                    (scope.userId != null && record.linkedUserId == scope.userId) ||
                            (record.cardId != null && record.cardId in scope.gatePersonCodes) ||
                            record.person in scope.gatePersonNames

                ScopeKind.DEPARTMENTS -> {
                    val code = record.departmentCode ?: departments?.toCode(record.dept)
                    code != null && code in scope.departmentCodes
                }
            }
        }
    }
}
