package top.foxball.cartask.scope

/**
 * ScopeQuerySupport 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */

import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Component
import top.foxball.cartask.entity.*
import top.foxball.cartask.repository.GatePersonRepository
import top.foxball.cartask.repository.ParkingOwnerRepository
import top.foxball.cartask.repository.ParkingPlateRepository
import top.foxball.cartask.shared.PlateNumbers


@Component
/**
 * ScopeQuerySupport 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
class ScopeQuerySupport(
    private val departmentLinkResolver: DepartmentLinkResolver,
    private val parkingOwnerRepository: ParkingOwnerRepository,
    private val parkingPlateRepository: ParkingPlateRepository,
    private val gatePersonRepository: GatePersonRepository,
) {
    
    /**
     * ownerVisibleInDepartments 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun ownerVisibleInDepartments(departments: DepartmentSnapshot, owner: ParkingOwner, scope: DataScope): Boolean {
        val code = owner.departmentCode ?: departments.toCode(owner.dept)
        return code != null && code in scope.departmentCodes
    }
    
    
    /**
     * ownersOf 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun ownersOf(userId: Long, phone: String?): List<ParkingOwner> {
        val linked = parkingOwnerRepository.findByLinkedUserId(userId)
        val byPhone = phone
            ?.let { parkingOwnerRepository.findByPhone(it).filter { owner -> owner.linkedUserId == null } }
            ?: emptyList()
        return (linked + byPhone).distinctBy { it.id }
    }
    
    
    /**
     * ownersInScope 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun ownersInScope(scope: DataScope): List<ParkingOwner> = when (scope.kind) {
        ScopeKind.ALL -> parkingOwnerRepository.findAll()
        ScopeKind.DEPARTMENTS -> {
            val departments = departmentLinkResolver.snapshot()
            parkingOwnerRepository.findAll().filter { ownerVisibleInDepartments(departments, it, scope) }
        }
        
        ScopeKind.SELF -> ownersOf(requireNotNull(scope.userId), scope.phone)
    }
    
    
    /**
     * ownerIdsInScope 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun ownerIdsInScope(scope: DataScope): Set<Long> =
        ownersInScope(scope).mapNotNull { it.id }.toSet()
    
    
    /**
     * ownerCardIdsInScope 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun ownerCardIdsInScope(scope: DataScope): Set<String> =
        ownersInScope(scope).map { it.cardId }.toSet()
    
    
    /**
     * plateNumbersInScope 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun plateNumbersInScope(scope: DataScope): Set<String> {
        if (scope.kind == ScopeKind.SELF) return scope.carNumbers
        val ownerIds = ownerIdsInScope(scope)
        if (ownerIds.isEmpty()) return emptySet()
        return PlateNumbers.normalizeAll(parkingPlateRepository.findByOwnerIdIn(ownerIds).map { it.plate })
    }
    
    
    /**
     * plateVisible 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun plateVisible(visibleOwnerIds: Set<Long>?, scopeUserId: Long?, plate: ParkingPlate): Boolean =
        visibleOwnerIds == null ||
                plate.ownerId in visibleOwnerIds ||
                (scopeUserId != null && plate.linkedUserId == scopeUserId)
    
    
    fun <T : DepartmentScoped> visibleInScope(scope: DataScope, rows: List<T>): List<T> =
        rows.scoped(scope, departmentLinkResolver.snapshot())
    
    
    /**
     * stampDepartmentCode 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun stampDepartmentCode(freeText: String?, current: String?): String? =
        departmentLinkResolver.snapshot().toCode(freeText) ?: current
    
    
    /**
     * stampOwnerCode 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun stampOwnerCode(ownerName: String?, current: String?): String? {
        if (ownerName.isNullOrBlank()) return current
        return parkingOwnerRepository.findAll().filter { it.name == ownerName }.singleOrNull()?.cardId ?: current
    }
    
    
    /**
     * spotVisible 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun spotVisible(visibleOwnerCodes: Set<String>?, ownerCode: String?): Boolean =
        visibleOwnerCodes == null || (ownerCode != null && ownerCode in visibleOwnerCodes)
    
    
    /**
     * accessRecordSpec 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun accessRecordSpec(scope: DataScope): Specification<AccessRecord> = Specification { root, _, cb ->
        when (scope.kind) {
            ScopeKind.ALL -> cb.conjunction()
            
            ScopeKind.SELF -> if (scope.carNumbers.isEmpty()) {
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
     * fileVisible 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun fileVisible(scope: DataScope, file: StoredFile): Boolean = when (scope.kind) {
        ScopeKind.ALL -> true
        
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
    
    
    /**
     * gatePersonCodesInScope 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun gatePersonCodesInScope(scope: DataScope): Set<String> = when (scope.kind) {
        ScopeKind.ALL -> emptySet()
        ScopeKind.SELF -> scope.gatePersonCodes
        ScopeKind.DEPARTMENTS -> gatePersonRepository.findAll()
            .filter { it.departmentCode != null && it.departmentCode in scope.departmentCodes }
            .map { it.code }
            .toSet()
    }
    
    
    /**
     * violationSubjectVisible 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
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
     * personRecordsInScope 函数：执行与该组件职责相关的业务操作。
     * 参数和返回值遵循调用方与领域服务之间的约定。
     */
    fun personRecordsInScope(scope: DataScope, rows: List<PersonAccessRecord>): List<PersonAccessRecord> {
        if (scope.kind == ScopeKind.ALL) return rows
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


