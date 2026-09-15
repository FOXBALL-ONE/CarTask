package top.foxball.cartask.scope

import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import top.foxball.cartask.repository.*
import top.foxball.cartask.shared.PlateNumbers

/** 单个资源的回填/未解析统计。 */
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

/**
 * 把存量数据里的部门自由文本与车牌补齐成稳定键。
 *
 * 为什么必须是显式接口而不是启动任务：`ddl-auto: update` 只会加列，不会回填；而这个项目没有
 * 迁移脚本。范围判定是 fail closed 的——**没回填的行对受限角色等于不存在**，所以回填必须在
 * 开通部门管理之前完成，并且要能随时查看还剩多少行没解析出来。
 *
 * 幂等：只处理目标列为空的行，重复执行不会改动已填好的数据。
 */
@Service
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
             * backfill：执行当前模块中的业务操作。
             *
             * 这是当前模块对外提供的处理入口，负责完成既定业务规则下的参数处理、核心计算和结果返回。
             * 调用过程中会沿用当前模块已有的校验、事务和异常传播约定，不改变原有业务行为。
             * @return 返回函数声明类型对应的处理结果；无返回值时表示操作已完成。
             */
    fun backfill(): BackfillSummary {
        // 本次回填统一复用一份部门快照。
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

        // 车位只有车主姓名，按卡号回填时只认唯一同名，重名宁可不填也不猜。
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

        // 人员进出记录顺带按卡号匹配门禁人员，补上归属账号——本表没有手机号，
        // 不回填这一列普通用户就只能靠姓名匹配（会重名），可靠性差得多。
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

        // 违规记录整条链上没有部门字段，归属落在主体上：车辆主体按车牌回到车主，
        // 人员主体按工号回到门禁人员。不回填的话违规对受限角色一律不可见。
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

        // 进出记录表最大，分批回填归一化车牌。
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

    /** 上线闸门：范围和回填都是 fail closed 的，这些数字不为零就意味着对应数据对受限角色不可见。 */
    @Transactional(readOnly = true)
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
            // 进出记录是最大的表，用计数查询，不要为了取个数字把它全读进内存。
            accessRecords = accessRecordRepository.countByCarNumberNormalizedIsNull().toInt(),
        )
    }

    private companion object {
        const val BACKFILL_BATCH_SIZE = 500
        val logger = LoggerFactory.getLogger(DepartmentLinkBackfillService::class.java)
    }
}
