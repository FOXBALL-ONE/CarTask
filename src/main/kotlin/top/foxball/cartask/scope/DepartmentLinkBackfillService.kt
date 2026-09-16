package top.foxball.cartask.scope

/**
 * DepartmentLinkBackfillService 组件。
 * 
 * 负责实现该文件声明的配置、领域模型或基础设施能力。
 */

import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import top.foxball.cartask.repository.*
import top.foxball.cartask.shared.PlateNumbers


data class BackfillCount(val scanned: Int, val resolved: Int, val unresolved: Int)

data class BackfillSummary(
    val owners: BackfillCount,
    val gatePersons: BackfillCount,
    val personRecords: BackfillCount,
    val spots: BackfillCount,
    val accessRecords: BackfillCount,
    val violationSubjects: BackfillCount,
)

data class UnlinkedSummary(
    val owners: Int,
    val gatePersons: Int,
    val personRecords: Int,
    val spots: Int,
    val accessRecords: Int,
    val violationSubjects: Int,
)


@Service
/**
 * DepartmentLinkBackfillService 的职责说明。
 * 该类型封装相关业务状态、依赖及操作流程。
 */
class DepartmentLinkBackfillService(
    private val departmentLinkResolver: DepartmentLinkResolver,
    private val parkingOwnerRepository: ParkingOwnerRepository,
    private val gatePersonRepository: GatePersonRepository,
    private val parkingSpotRepository: ParkingSpotRepository,
    private val personAccessRecordRepository: PersonAccessRecordRepository,
    private val accessRecordRepository: AccessRecordRepository,
    private val userRepository: UserRepository,
    private val parkingPlateRepository: ParkingPlateRepository,
    private val violationSubjectRepository: ViolationSubjectRepository,
) {
    @Transactional
            
            
            /**
             * backfill 函数：执行与该组件职责相关的业务操作。
             * 参数和返回值遵循调用方与领域服务之间的约定。
             */
    fun backfill(): BackfillSummary {
        val departments = departmentLinkResolver.snapshot()
        
        val owners = parkingOwnerRepository.findAll()
        var ownerResolved = 0
        owners.filter { it.departmentCode == null }.forEach { owner ->
            departments.toCode(owner.dept)?.let {
                owner.departmentCode = it
                ownerResolved++
            }
        }
        parkingOwnerRepository.saveAll(owners)
        
        val gatePersons = gatePersonRepository.findAll()
        var gatePersonResolved = 0
        gatePersons.filter { it.departmentCode == null }.forEach { person ->
            departments.toCode(person.dept)?.let {
                person.departmentCode = it
                gatePersonResolved++
            }
        }
        gatePersonRepository.saveAll(gatePersons)
        
        val spots = parkingSpotRepository.findAll()
        val ownerCodeByName = owners.groupBy { it.name }.filterValues { it.size == 1 }
            .mapValues { (_, value) -> value.single().cardId }
        var spotResolved = 0
        spots.filter { it.ownerCode == null }.forEach { spot ->
            spot.owner?.let { name -> ownerCodeByName[name] }?.let {
                spot.ownerCode = it
                spotResolved++
            }
        }
        parkingSpotRepository.saveAll(spots)
        
        val gatePersonByCode = gatePersons.associateBy { it.code }
        val userIdByPhone = userRepository.findAll()
            .mapNotNull { user -> user.phone?.trim()?.takeIf(String::isNotEmpty)?.let { it to user.id } }
            .toMap()
        val personRecords = personAccessRecordRepository.findAll()
        var personResolved = 0
        personRecords.forEach { record ->
            var changed = false
            if (record.departmentCode == null) {
                departments.toCode(record.dept)?.let { record.departmentCode = it; changed = true }
            }
            val matched = record.cardId?.let { gatePersonByCode[it] }
            if (matched != null) {
                if (record.linkedUserId == null) {
                    userIdByPhone[matched.phone.trim()]?.let { record.linkedUserId = it; changed = true }
                }
                if (record.departmentCode == null && matched.departmentCode != null) {
                    matched.departmentCode?.let { record.departmentCode = it; changed = true }
                }
            }
            if (changed) personResolved++
        }
        personAccessRecordRepository.saveAll(personRecords)
        
        val subjectPlateOwners = parkingPlateRepository.findAll()
            .associate { PlateNumbers.normalize(it.plate) to it.ownerId }
        val subjects = violationSubjectRepository.findAll()
        var subjectResolved = 0
        subjects.forEach { subject ->
            var changed = false
            val ownerId = subjectPlateOwners[PlateNumbers.normalize(subject.subjectNumber)]
            val owner = ownerId?.let { id -> owners.firstOrNull { it.id == id } }
            val gatePerson = gatePersonByCode[subject.subjectNumber]
                ?: gatePersons.firstOrNull { it.name == subject.subjectName }
            val departmentCode = owner?.departmentCode
                ?: departments.toCode(owner?.dept)
                ?: gatePerson?.departmentCode
                ?: departments.toCode(gatePerson?.dept)
            val linkedUserId = owner?.linkedUserId
                ?: owner?.phone?.trim()?.takeIf(String::isNotEmpty)?.let { userIdByPhone[it] }
                ?: gatePerson?.phone?.trim()?.takeIf(String::isNotEmpty)?.let { userIdByPhone[it] }
            if (subject.departmentCode == null && departmentCode != null) {
                subject.departmentCode = departmentCode
                changed = true
            }
            if (subject.linkedUserId == null && linkedUserId != null) {
                subject.linkedUserId = linkedUserId
                changed = true
            }
            if (changed) subjectResolved++
        }
        violationSubjectRepository.saveAll(subjects)
        
        var accessScanned = 0
        var accessResolved = 0
        var page = 0
        while (true) {
            val batch = accessRecordRepository.findAll(PageRequest.of(page, BACKFILL_BATCH_SIZE))
            if (batch.isEmpty) break
            val pending = batch.content.filter { it.carNumberNormalized == null }
            if (pending.isNotEmpty()) {
                pending.forEach { it.syncCarNumberNormalized() }
                accessRecordRepository.saveAll(pending)
            }
            accessScanned += batch.numberOfElements
            accessResolved += pending.size
            if (!batch.hasNext()) break
            page++
        }
        
        logger.info(
            "归属回填完成：车主 {}/{}，门禁人员 {}/{}，人员进出 {}/{}，车位 {}/{}，违规主体 {}/{}，进出记录 {}/{}",
            ownerResolved, owners.size,
            gatePersonResolved, gatePersons.size,
            personResolved, personRecords.size,
            spotResolved, spots.size,
            subjectResolved, subjects.size,
            accessResolved, accessScanned,
        )
        return BackfillSummary(
            owners = BackfillCount(owners.size, ownerResolved, owners.size - ownerResolved),
            gatePersons = BackfillCount(gatePersons.size, gatePersonResolved, gatePersons.size - gatePersonResolved),
            personRecords = BackfillCount(personRecords.size, personResolved, personRecords.size - personResolved),
            spots = BackfillCount(spots.size, spotResolved, spots.size - spotResolved),
            accessRecords = BackfillCount(accessScanned, accessResolved, accessScanned - accessResolved),
            violationSubjects = BackfillCount(subjects.size, subjectResolved, subjects.size - subjectResolved),
        )
    }
    
    
    @Transactional(readOnly = true)
            /**
             * unlinkedSummary 函数：执行与该组件职责相关的业务操作。
             * 参数和返回值遵循调用方与领域服务之间的约定。
             */
    fun unlinkedSummary(): UnlinkedSummary {
        val departments = departmentLinkResolver.snapshot()
        return UnlinkedSummary(
            owners = parkingOwnerRepository.findAll()
                .count { (it.departmentCode ?: departments.toCode(it.dept)) == null },
            gatePersons = gatePersonRepository.findAll()
                .count { (it.departmentCode ?: departments.toCode(it.dept)) == null },
            personRecords = personAccessRecordRepository.findAll()
                .count { (it.departmentCode ?: departments.toCode(it.dept)) == null },
            spots = parkingSpotRepository.findAll().count { it.ownerCode == null },
            violationSubjects = violationSubjectRepository.findAll()
                .count { (it.departmentCode ?: departments.toCode(it.subjectName)) == null },
            accessRecords = accessRecordRepository.countByCarNumberNormalizedIsNull().toInt(),
        )
    }
    
    private companion object {
        const val BACKFILL_BATCH_SIZE = 500
        val logger = LoggerFactory.getLogger(DepartmentLinkBackfillService::class.java)
    }
}


