package top.foxball.cartask.config

import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import top.foxball.cartask.entity.AccessControl
import top.foxball.cartask.entity.AccessRecord
import top.foxball.cartask.entity.CarMasterInfo
import top.foxball.cartask.entity.Department
import top.foxball.cartask.entity.Device
import top.foxball.cartask.entity.GateDeleteRequest
import top.foxball.cartask.entity.GatePerson
import top.foxball.cartask.entity.PersonAccessRecord
import top.foxball.cartask.entity.ParkingOwner
import top.foxball.cartask.entity.ParkingPlate
import top.foxball.cartask.entity.ParkingSpot
import top.foxball.cartask.entity.Position
import top.foxball.cartask.entity.Role
import top.foxball.cartask.entity.User
import top.foxball.cartask.entity.ViolationRecord
import top.foxball.cartask.entity.ViolationSubject
import top.foxball.cartask.entity.type.AccessControlType
import top.foxball.cartask.entity.type.CarType
import top.foxball.cartask.entity.type.LicensePlateType
import top.foxball.cartask.entity.type.ReleaseType
import top.foxball.cartask.entity.type.RestrictionType
import top.foxball.cartask.entity.type.ViolationType
import top.foxball.cartask.entity.type.ZoneType
import top.foxball.cartask.repository.AccessControlRepository
import top.foxball.cartask.repository.AccessControlTypeRepository
import top.foxball.cartask.repository.AccessRecordRepository
import top.foxball.cartask.repository.CarMasterInfoRepository
import top.foxball.cartask.repository.CarTypeRepository
import top.foxball.cartask.repository.DepartmentRepository
import top.foxball.cartask.repository.DeviceRepository
import top.foxball.cartask.repository.GateDeleteRequestRepository
import top.foxball.cartask.repository.GatePersonRepository
import top.foxball.cartask.repository.LicensePlateTypeRepository
import top.foxball.cartask.repository.ParkingOwnerRepository
import top.foxball.cartask.repository.ParkingPlateRepository
import top.foxball.cartask.repository.ParkingSpotRepository
import top.foxball.cartask.repository.PersonAccessRecordRepository
import top.foxball.cartask.repository.PositionRepository
import top.foxball.cartask.repository.RoleRepository
import top.foxball.cartask.repository.ReleaseTypeRepository
import top.foxball.cartask.repository.RestrictionTypeRepository
import top.foxball.cartask.repository.UserRepository
import top.foxball.cartask.repository.ViolationRecordRepository
import top.foxball.cartask.repository.ViolationSubjectRepository
import top.foxball.cartask.repository.ViolationTypeRepository
import top.foxball.cartask.repository.ZoneTypeRepository
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 在应用完全启动后写入一组可重复执行的开发模拟数据。
 *
 * 所有样例记录都使用 MOCK 前缀、固定业务编号或固定名称识别，因此重复启动不会产生重复数据。
 * 该组件默认关闭，只能通过 MOCK_DATA_ENABLED=true 显式启用。
 */
@Component
@ConditionalOnProperty(prefix = "app.mock-data", name = ["enabled"], havingValue = "true")
class MockDataInitializer(
    private val properties: MockDataProperties,
    private val passwordEncoder: PasswordEncoder,
    private val departmentRepository: DepartmentRepository,
    private val positionRepository: PositionRepository,
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val deviceRepository: DeviceRepository,
    private val carTypeRepository: CarTypeRepository,
    private val licensePlateTypeRepository: LicensePlateTypeRepository,
    private val accessControlTypeRepository: AccessControlTypeRepository,
    private val releaseTypeRepository: ReleaseTypeRepository,
    private val restrictionTypeRepository: RestrictionTypeRepository,
    private val zoneTypeRepository: ZoneTypeRepository,
    private val carMasterInfoRepository: CarMasterInfoRepository,
    private val accessControlRepository: AccessControlRepository,
    private val accessRecordRepository: AccessRecordRepository,
    private val parkingOwnerRepository: ParkingOwnerRepository,
    private val parkingSpotRepository: ParkingSpotRepository,
    private val parkingPlateRepository: ParkingPlateRepository,
    private val gatePersonRepository: GatePersonRepository,
    private val gateDeleteRequestRepository: GateDeleteRequestRepository,
    private val personAccessRecordRepository: PersonAccessRecordRepository,
    private val violationTypeRepository: ViolationTypeRepository,
    private val violationSubjectRepository: ViolationSubjectRepository,
    private val violationRecordRepository: ViolationRecordRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @EventListener(classes = [ApplicationReadyEvent::class])
    @Transactional
    fun write() {
        if (!properties.enabled) {
            logger.debug("模拟数据写入未启用")
            return
        }

        val now = (properties.referenceTime ?: LocalDateTime.now()).withNano(0)
        val headquarters = ensure(
            departmentRepository.findByDepartmentNumber("MOCK-OPS"),
            {
                Department().apply {
                    name = "模拟运营中心"
                    departmentNumber = "MOCK-OPS"
                    sortOrder = 1
                    director = "模拟管理员"
                    contactPhone = "13800000000"
                }
            },
            departmentRepository::save,
        )
        val parkingDepartment = ensure(
            departmentRepository.findByDepartmentNumber("MOCK-PARKING"),
            {
                Department().apply {
                    name = "模拟停车管理组"
                    departmentNumber = "MOCK-PARKING"
                    superior = headquarters
                    sortOrder = 1
                    director = "模拟运营员"
                    contactPhone = "13800000001"
                }
            },
            departmentRepository::save,
        )
        val securityDepartment = ensure(
            departmentRepository.findByDepartmentNumber("MOCK-SECURITY"),
            {
                Department().apply {
                    name = "模拟门禁安全组"
                    departmentNumber = "MOCK-SECURITY"
                    superior = headquarters
                    sortOrder = 2
                    director = "模拟安全主管"
                    contactPhone = "13800000004"
                }
            },
            departmentRepository::save,
        )
        val propertyDepartment = ensure(
            departmentRepository.findByDepartmentNumber("MOCK-PROPERTY"),
            {
                Department().apply {
                    name = "模拟物业服务组"
                    departmentNumber = "MOCK-PROPERTY"
                    superior = headquarters
                    sortOrder = 3
                    director = "模拟物业主管"
                    contactPhone = "13800000005"
                }
            },
            departmentRepository::save,
        )
        val operatorPosition = ensure(
            positionRepository.findByCodeNumber("MOCK-OPERATOR"),
            {
                Position().apply {
                    name = "模拟运营员"
                    codeNumber = "MOCK-OPERATOR"
                    orderNumber = 1
                }
            },
            positionRepository::save,
        )
        val securityPosition = ensure(
            positionRepository.findByCodeNumber("MOCK-SECURITY"),
            {
                Position().apply {
                    name = "模拟安全主管"
                    codeNumber = "MOCK-SECURITY"
                    orderNumber = 2
                }
            },
            positionRepository::save,
        )
        val propertyPosition = ensure(
            positionRepository.findByCodeNumber("MOCK-PROPERTY"),
            {
                Position().apply {
                    name = "模拟物业专员"
                    codeNumber = "MOCK-PROPERTY"
                    orderNumber = 3
                }
            },
            positionRepository::save,
        )
        val userRole = ensure(
            roleRepository.findByNameIgnoreCase("USER"),
            {
                Role().apply {
                    name = "USER"
                    description = "普通用户"
                    enabled = true
                }
            },
            roleRepository::save,
        )
        ensure(
            userRepository.findByUsername("mock.operator"),
            {
                User().apply {
                    username = "mock.operator"
                    nickName = "模拟运营员"
                    email = "mock.operator@example.com"
                    passwordHash = passwordEncoder.encode("mock-password").toString()
                    phone = "13800000001"
                    gender = User.Gender.UNKNOWN
                    department = parkingDepartment
                    position = operatorPosition
                    role = "USER"
                    status = User.Status.Activity
                    enabled = true
                    createdAt = now
                    updatedAt = now
                    roles = linkedSetOf(userRole)
                }
            },
            userRepository::save,
        )
        ensure(
            userRepository.findByUsername("mock.security"),
            {
                User().apply {
                    username = "mock.security"
                    nickName = "模拟安全主管"
                    email = "mock.security@example.com"
                    passwordHash = passwordEncoder.encode("mock-password").toString()
                    phone = "13800000004"
                    gender = User.Gender.MALE
                    department = securityDepartment
                    position = securityPosition
                    role = "USER"
                    roles = linkedSetOf(userRole)
                    status = User.Status.Activity
                    enabled = true
                    createdAt = now
                    updatedAt = now
                }
            },
            userRepository::save,
        )
        ensure(
            userRepository.findByUsername("mock.property"),
            {
                User().apply {
                    username = "mock.property"
                    nickName = "模拟物业专员"
                    email = "mock.property@example.com"
                    passwordHash = passwordEncoder.encode("mock-password").toString()
                    phone = "13800000005"
                    gender = User.Gender.FEMALE
                    department = propertyDepartment
                    position = propertyPosition
                    role = "USER"
                    roles = linkedSetOf(userRole)
                    status = User.Status.Activity
                    enabled = true
                    createdAt = now
                    updatedAt = now
                }
            },
            userRepository::save,
        )
        ensure(
            deviceRepository.findByDeviceCode("MOCK-GATE-01"),
            {
                Device().apply {
                    deviceName = "模拟东门道闸"
                    deviceCode = "MOCK-GATE-01"
                    deviceType = "ACCESS_GATE"
                    brand = "Keytop"
                    model = "KT-GATE-01"
                    location = "模拟园区东门"
                    ip = "192.168.10.21"
                    installDate = now.toLocalDate().minusMonths(6).toString()
                    orderNumber = 1
                    createdAt = now
                    updatedAt = now
                }
            },
            deviceRepository::save,
        )
        ensure(
            deviceRepository.findByDeviceCode("MOCK-GATE-02"),
            {
                Device().apply {
                    deviceName = "模拟西门道闸"
                    deviceCode = "MOCK-GATE-02"
                    deviceType = "ACCESS_GATE"
                    brand = "Keytop"
                    model = "KT-GATE-01"
                    location = "模拟园区西门"
                    ip = "192.168.10.22"
                    installDate = now.toLocalDate().minusMonths(5).toString()
                    orderNumber = 2
                    createdAt = now
                    updatedAt = now
                }
            },
            deviceRepository::save,
        )
        ensure(
            deviceRepository.findByDeviceCode("MOCK-CAMERA-01"),
            {
                Device().apply {
                    deviceName = "模拟东区抓拍相机"
                    deviceCode = "MOCK-CAMERA-01"
                    deviceType = "CAMERA"
                    brand = "Hikvision"
                    model = "DS-2CD2T"
                    location = "模拟园区东区"
                    ip = "192.168.10.31"
                    installDate = now.toLocalDate().minusMonths(4).toString()
                    orderNumber = 3
                    createdAt = now
                    updatedAt = now
                }
            },
            deviceRepository::save,
        )
        ensure(
            deviceRepository.findByDeviceCode("MOCK-INTERCOM-01"),
            {
                Device().apply {
                    deviceName = "模拟访客对讲机"
                    deviceCode = "MOCK-INTERCOM-01"
                    deviceType = "INTERCOM"
                    brand = "Hikvision"
                    model = "DS-KH"
                    location = "模拟园区西门"
                    ip = "192.168.10.41"
                    installDate = now.toLocalDate().minusMonths(3).toString()
                    orderNumber = 4
                    createdAt = now
                    updatedAt = now
                }
            },
            deviceRepository::save,
        )

        val carType = ensure(
            carTypeRepository.findFirstByCarName("模拟小型客车"),
            {
                CarType().apply {
                    carName = "模拟小型客车"
                    orderNumber = 1
                }
            },
            carTypeRepository::save,
        )
        val suvCarType = ensure(
            carTypeRepository.findFirstByCarName("模拟SUV客车"),
            {
                CarType().apply {
                    carName = "模拟SUV客车"
                    orderNumber = 2
                }
            },
            carTypeRepository::save,
        )
        val truckCarType = ensure(
            carTypeRepository.findFirstByCarName("模拟轻型货车"),
            {
                CarType().apply {
                    carName = "模拟轻型货车"
                    orderNumber = 3
                }
            },
            carTypeRepository::save,
        )
        val licensePlateType = ensure(
            licensePlateTypeRepository.findFirstByCarType("模拟蓝牌"),
            {
                LicensePlateType().apply {
                    this.carType = "模拟蓝牌"
                    orderNumber = 1
                }
            },
            licensePlateTypeRepository::save,
        )
        val yellowLicensePlateType = ensure(
            licensePlateTypeRepository.findFirstByCarType("模拟黄牌"),
            {
                LicensePlateType().apply {
                    this.carType = "模拟黄牌"
                    orderNumber = 2
                }
            },
            licensePlateTypeRepository::save,
        )
        val newEnergyLicensePlateType = ensure(
            licensePlateTypeRepository.findFirstByCarType("模拟新能源绿牌"),
            {
                LicensePlateType().apply {
                    this.carType = "模拟新能源绿牌"
                    orderNumber = 3
                }
            },
            licensePlateTypeRepository::save,
        )
        val accessControlType = ensure(
            accessControlTypeRepository.findByAccessControlName("模拟长期授权"),
            {
                AccessControlType().apply {
                    accessControlName = "模拟长期授权"
                    orderNumber = 1
                }
            },
            accessControlTypeRepository::save,
        )
        val temporaryAccessControlType = ensure(
            accessControlTypeRepository.findByAccessControlName("模拟临时访客授权"),
            {
                AccessControlType().apply {
                    accessControlName = "模拟临时访客授权"
                    orderNumber = 2
                }
            },
            accessControlTypeRepository::save,
        )
        ensure(
            releaseTypeRepository.findFirstByReleaseName("模拟人工放行"),
            {
                ReleaseType().apply {
                    releaseName = "模拟人工放行"
                    orderNumber = 1
                }
            },
            releaseTypeRepository::save,
        )
        ensure(
            releaseTypeRepository.findFirstByReleaseName("模拟自动放行"),
            {
                ReleaseType().apply {
                    releaseName = "模拟自动放行"
                    orderNumber = 2
                }
            },
            releaseTypeRepository::save,
        )
        ensure(
            releaseTypeRepository.findFirstByReleaseName("模拟远程放行"),
            {
                ReleaseType().apply {
                    releaseName = "模拟远程放行"
                    orderNumber = 3
                }
            },
            releaseTypeRepository::save,
        )
        ensure(
            restrictionTypeRepository.findFirstByRestrictionName("模拟临时限制"),
            {
                RestrictionType().apply {
                    restrictionName = "模拟临时限制"
                    orderNumber = 1
                }
            },
            restrictionTypeRepository::save,
        )
        ensure(
            restrictionTypeRepository.findFirstByRestrictionName("模拟黑名单限制"),
            {
                RestrictionType().apply {
                    restrictionName = "模拟黑名单限制"
                    orderNumber = 2
                }
            },
            restrictionTypeRepository::save,
        )
        ensure(
            restrictionTypeRepository.findFirstByRestrictionName("模拟超时限制"),
            {
                RestrictionType().apply {
                    restrictionName = "模拟超时限制"
                    orderNumber = 3
                }
            },
            restrictionTypeRepository::save,
        )
        val zone = ensure(
            zoneTypeRepository.findByZoneCode("MOCK-ZONE-01"),
            {
                ZoneType().apply {
                    zoneCode = "MOCK-ZONE-01"
                    zoneName = "模拟东区"
                    orderNumber = 1
                }
            },
            zoneTypeRepository::save,
        )
        val southZone = ensure(
            zoneTypeRepository.findByZoneCode("MOCK-ZONE-02"),
            {
                ZoneType().apply {
                    zoneCode = "MOCK-ZONE-02"
                    zoneName = "模拟南区"
                    orderNumber = 2
                }
            },
            zoneTypeRepository::save,
        )
        val westZone = ensure(
            zoneTypeRepository.findByZoneCode("MOCK-ZONE-03"),
            {
                ZoneType().apply {
                    zoneCode = "MOCK-ZONE-03"
                    zoneName = "模拟西区"
                    orderNumber = 3
                }
            },
            zoneTypeRepository::save,
        )

        val ownerOne = ensure(
            parkingOwnerRepository.findByCardId("MOCK-OWNER-CARD-001"),
            {
                ParkingOwner().apply {
                    cardId = "MOCK-OWNER-CARD-001"
                    name = "模拟车辆联系人"
                    dept = parkingDepartment.name
                    phone = "13800000002"
                    spotCount = 2
                    plateCount = 1
                    balance = BigDecimal("128.50")
                    status = 1
                    createdAt = now
                    updatedAt = now
                }
            },
            parkingOwnerRepository::save,
        )
        val ownerTwo = ensure(
            parkingOwnerRepository.findByCardId("MOCK-OWNER-CARD-002"),
            {
                ParkingOwner().apply {
                    cardId = "MOCK-OWNER-CARD-002"
                    name = "模拟企业车主"
                    dept = propertyDepartment.name
                    phone = "13800000006"
                    spotCount = 2
                    plateCount = 1
                    balance = BigDecimal("86.00")
                    status = 1
                    createdAt = now
                    updatedAt = now
                }
            },
            parkingOwnerRepository::save,
        )
        val ownerThree = ensure(
            parkingOwnerRepository.findByCardId("MOCK-OWNER-CARD-003"),
            {
                ParkingOwner().apply {
                    cardId = "MOCK-OWNER-CARD-003"
                    name = "模拟配送车主"
                    dept = securityDepartment.name
                    phone = "13800000007"
                    spotCount = 0
                    plateCount = 1
                    balance = BigDecimal("32.75")
                    status = 0
                    createdAt = now
                    updatedAt = now
                }
            },
            parkingOwnerRepository::save,
        )
        listOf(
            Triple("MOCK-SPOT-A-001", "模拟东区", "标准车位"),
            Triple("MOCK-SPOT-A-002", "模拟东区", "充电车位"),
            Triple("MOCK-SPOT-B-101", "模拟南区", "标准车位"),
            Triple("MOCK-SPOT-B-102", "模拟南区", "充电车位"),
            Triple("MOCK-SPOT-C-008", "模拟西区", "货车位"),
        ).forEachIndexed { index, (code, area, type) ->
            val owner = when (index) {
                0, 1 -> ownerOne
                2, 3 -> ownerTwo
                else -> ownerThree
            }
            ensure(
                parkingSpotRepository.findByCode(code),
                {
                    ParkingSpot().apply {
                        this.code = code
                        this.area = area
                        this.type = type
                        this.owner = owner.name.takeIf { index != 4 }
                        status = if (index == 4) 0 else 1
                        remark = if (index == 1 || index == 3) "支持新能源车辆充电" else null
                        createdAt = now
                        updatedAt = now
                    }
                },
                parkingSpotRepository::save,
            )
        }
        listOf(
            Triple("粤A·MOCK01", ownerOne, now.toLocalDate().minusMonths(8)),
            Triple("粤A·MOCK02", ownerTwo, now.toLocalDate().minusMonths(4)),
            Triple("粤A·MOCK03", ownerThree, now.toLocalDate().minusMonths(2)),
        ).forEach { (plateNumber, owner, registeredAt) ->
            ensure(
                parkingPlateRepository.findByPlate(plateNumber),
                {
                    ParkingPlate().apply {
                        plate = plateNumber
                        this.owner = owner.name
                        ownerId = requireNotNull(owner.id)
                        status = if (plateNumber == "粤A·MOCK03") 0 else 1
                        regDate = registeredAt
                        createdAt = now
                        updatedAt = now
                    }
                },
                parkingPlateRepository::save,
            )
        }

        val gatePersonOne = ensure(
            gatePersonRepository.findByCode("MOCK-GATE-PERSON-001"),
            {
                GatePerson().apply {
                    code = "MOCK-GATE-PERSON-001"
                    dept = parkingDepartment.name
                    name = "模拟访客"
                    phone = "13800000003"
                    idCard = "440100199001010011"
                    face = "mock-face-profile-001"
                    createTime = now.minusDays(5)
                    approveStatus = GatePerson.ApproveStatus.APPROVED
                    syncStatus = GatePerson.SyncStatus.SYNCED
                    updatedAt = now
                }
            },
            gatePersonRepository::save,
        )
        val gatePersonTwo = ensure(
            gatePersonRepository.findByCode("MOCK-GATE-PERSON-002"),
            {
                GatePerson().apply {
                    code = "MOCK-GATE-PERSON-002"
                    dept = propertyDepartment.name
                    name = "模拟企业访客"
                    phone = "13800000008"
                    idCard = "440100199202020022"
                    face = "mock-face-profile-002"
                    createTime = now.minusDays(2)
                    approveStatus = GatePerson.ApproveStatus.PENDING
                    syncStatus = GatePerson.SyncStatus.NOT_SYNCED
                    updatedAt = now
                }
            },
            gatePersonRepository::save,
        )
        ensure(
            gateDeleteRequestRepository.findAll().firstOrNull { it.code == gatePersonTwo.code },
            {
                GateDeleteRequest().apply {
                    personId = requireNotNull(gatePersonTwo.id)
                    code = gatePersonTwo.code
                    dept = gatePersonTwo.dept
                    // 与真实写路径一致：申请单要落部门编码，否则列表只能靠部门名兜底解析。
                    departmentCode = gatePersonTwo.departmentCode
                    name = gatePersonTwo.name
                    phone = gatePersonTwo.phone
                    idCard = gatePersonTwo.idCard
                    face = gatePersonTwo.face
                    reason = "模拟人员离开园区"
                    applyTime = now.minusHours(4)
                    status = GateDeleteRequest.Status.PENDING
                }
            },
            gateDeleteRequestRepository::save,
        )
        listOf(
            Triple(gatePersonOne, "进", now.minusHours(4)),
            Triple(gatePersonOne, "出", now.minusHours(2)),
            Triple(gatePersonTwo, "进", now.minusMinutes(45)),
        ).forEachIndexed { index, (person, direction, time) ->
            ensure(
                personAccessRecordRepository.findAll().firstOrNull { it.person == person.name && it.direction == direction },
                {
                    PersonAccessRecord().apply {
                        this.person = person.name
                        cardId = "MOCK-PERSON-CARD-${index + 1}"
                        dept = person.dept
                        this.time = time
                        this.direction = direction
                        gate = if (index == 2) "模拟西门道闸" else "模拟东门道闸"
                        method = if (index == 2) "人工核验" else "人脸识别"
                        status = if (index == 2) "待复核" else "正常"
                        photo = person.face
                    }
                },
                personAccessRecordRepository::save,
            )
        }

        val violationType = ensure(
            violationTypeRepository.findFirstByViolationName("模拟超时停车"),
            {
                ViolationType().apply {
                    violationName = "模拟超时停车"
                    violationFraction = 2
                    violationContent = "模拟车辆超过允许停车时长"
                    orderNumber = 1
                    createdAt = now
                    updatedAt = now
                }
            },
            violationTypeRepository::save,
        )
        val violationSubject = ensure(
            violationSubjectRepository.findBySubjectNumber("粤A·MOCK03"),
            {
                ViolationSubject().apply {
                    subjectType = ViolationSubject.SubjectType.VEHICLE
                    subjectName = "模拟配送车主"
                    subjectNumber = "粤A·MOCK03"
                    phone = "13800000007"
                    remark = "模拟异常车辆"
                    createdAt = now
                    updatedAt = now
                }
            },
            violationSubjectRepository::save,
        )
        ensure(
            violationRecordRepository.findAll().firstOrNull { it.subject.subjectNumber == violationSubject.subjectNumber && it.violationType.violationName == violationType.violationName },
            {
                ViolationRecord().apply {
                    subject = violationSubject
                    this.violationType = violationType
                    violationTime = now.minusDays(1).withHour(12)
                    violationLocation = westZone.zoneName
                    violationFraction = violationType.violationFraction ?: 0
                    violationContent = violationType.violationContent
                    evidenceInfo = "mock-evidence://vehicle/MOCK03"
                    handlingStatus = ViolationRecord.HandlingStatus.PENDING
                    createdAt = now
                    updatedAt = now
                }
            },
            violationRecordRepository::save,
        )

        ensure(
            carMasterInfoRepository.findFirstByCarCardNumber("MOCK-CARD-001"),
            {
                CarMasterInfo().apply {
                    carMasterName = "模拟车辆联系人"
                    carMasterPhone = "13800000002"
                    department = parkingDepartment
                    linkAddress = "模拟园区东区"
                    carCardNumber = "MOCK-CARD-001"
                    assistantInfo = "模拟运营员"
                    this.carType = carType
                    updateTime = now
                    locationInfo = zone.zoneName
                    parkingSpaces += CarMasterInfo.ParkingSpaceItem().apply {
                        this.licensePlateType = licensePlateType
                        parkingSpaceCount = 2
                        locationInfo = "A-001、A-002"
                    }
                    cards += CarMasterInfo.CarCardItem().apply {
                        carNumber = "粤A·MOCK01"
                        entryExitVoucher = "MOCK-VOUCHER-001"
                        remark = "启动模拟数据"
                    }
                }
            },
            carMasterInfoRepository::save,
        )
        ensure(
            carMasterInfoRepository.findFirstByCarCardNumber("MOCK-CARD-002"),
            {
                CarMasterInfo().apply {
                    carMasterName = "模拟企业车主"
                    carMasterPhone = "13800000006"
                    department = propertyDepartment
                    linkAddress = "模拟园区南区"
                    carCardNumber = "MOCK-CARD-002"
                    assistantInfo = "模拟物业专员"
                    this.carType = suvCarType
                    updateTime = now
                    locationInfo = southZone.zoneName
                    parkingSpaces += CarMasterInfo.ParkingSpaceItem().apply {
                        this.licensePlateType = newEnergyLicensePlateType
                        parkingSpaceCount = 2
                        locationInfo = "B-101、B-102"
                    }
                    cards += CarMasterInfo.CarCardItem().apply {
                        carNumber = "粤A·MOCK02"
                        entryExitVoucher = "MOCK-VOUCHER-002"
                        remark = "新能源车辆"
                    }
                }
            },
            carMasterInfoRepository::save,
        )
        ensure(
            carMasterInfoRepository.findFirstByCarCardNumber("MOCK-CARD-003"),
            {
                CarMasterInfo().apply {
                    carMasterName = "模拟配送车主"
                    carMasterPhone = "13800000007"
                    department = securityDepartment
                    linkAddress = "模拟园区西区"
                    carCardNumber = "MOCK-CARD-003"
                    assistantInfo = "模拟安全主管"
                    this.carType = truckCarType
                    updateTime = now
                    endTime = now.plusDays(90)
                    locationInfo = westZone.zoneName
                    parkingSpaces += CarMasterInfo.ParkingSpaceItem().apply {
                        this.licensePlateType = yellowLicensePlateType
                        parkingSpaceCount = 1
                        locationInfo = "C-008"
                    }
                    cards += CarMasterInfo.CarCardItem().apply {
                        carNumber = "粤A·MOCK03"
                        entryExitVoucher = "MOCK-VOUCHER-003"
                        status = false
                        remark = "临时配送车辆"
                    }
                }
            },
            carMasterInfoRepository::save,
        )
        ensure(
            accessControlRepository.findByPersonNumber("MOCK-PERSON-001"),
            {
                AccessControl().apply {
                    name = "模拟访客"
                    phone = "13800000003"
                    personNumber = "MOCK-PERSON-001"
                    faceInfo = "mock-face-profile-001"
                    accessControlPermission = accessControlType
                    upTime = now.minusDays(1)
                    endTime = now.plusDays(30)
                    accessControlList = "东门、办公楼"
                    department = parkingDepartment
                    reviewStatus = AccessControl.ReviewStatus.APPROVED
                    synchronizedLoading = true
                }
            },
            accessControlRepository::save,
        )
        ensure(
            accessControlRepository.findByPersonNumber("MOCK-PERSON-002"),
            {
                AccessControl().apply {
                    name = "模拟企业访客"
                    phone = "13800000008"
                    personNumber = "MOCK-PERSON-002"
                    faceInfo = "mock-face-profile-002"
                    accessControlPermission = temporaryAccessControlType
                    upTime = now.minusHours(6)
                    endTime = now.plusDays(2)
                    accessControlList = "西门、访客中心"
                    department = propertyDepartment
                    reviewStatus = AccessControl.ReviewStatus.PENDING
                    synchronizedLoading = false
                }
            },
            accessControlRepository::save,
        )
        ensure(
            accessControlRepository.findByPersonNumber("MOCK-PERSON-003"),
            {
                AccessControl().apply {
                    name = "模拟临时司机"
                    phone = "13800000009"
                    personNumber = "MOCK-PERSON-003"
                    faceInfo = "mock-face-profile-003"
                    accessControlPermission = temporaryAccessControlType
                    upTime = now.minusDays(3)
                    endTime = now.minusDays(1)
                    accessControlList = "南门、装卸区"
                    department = securityDepartment
                    reviewStatus = AccessControl.ReviewStatus.REJECTED
                    synchronizedLoading = false
                }
            },
            accessControlRepository::save,
        )
        ensure(
            accessRecordRepository.findByIdentity("粤A·MOCK01", AccessRecord.InAndOut.IN, now.minusHours(2))
                ?: accessRecordRepository.findFirstByCarNumberAndInAndOutOrderByInAndOutTimeDesc("粤A·MOCK01", AccessRecord.InAndOut.IN),
            {
                AccessRecord().apply {
                    carNumber = "粤A·MOCK01"
                    inAndOut = AccessRecord.InAndOut.IN
                    inAndOutTime = now.minusHours(2)
                    this.carType = carType
                    departmentName = parkingDepartment.name
                    releaseChannel = AccessRecord.ReleaseChannel.AUTOMATIC
                    carOwnerName = "模拟车辆联系人"
                    gateName = "模拟东门道闸"
                    vehicleTypeName = carType.carName
                }
            },
            accessRecordRepository::save,
        )
        ensure(
            accessRecordRepository.findByIdentity("粤A·MOCK01", AccessRecord.InAndOut.OUT, now.minusHours(1))
                ?: accessRecordRepository.findFirstByCarNumberAndInAndOutOrderByInAndOutTimeDesc("粤A·MOCK01", AccessRecord.InAndOut.OUT),
            {
                AccessRecord().apply {
                    carNumber = "粤A·MOCK01"
                    inAndOut = AccessRecord.InAndOut.OUT
                    inAndOutTime = now.minusHours(1)
                    this.carType = carType
                    departmentName = parkingDepartment.name
                    releaseInstructions = "模拟授权放行"
                    releaseChannel = AccessRecord.ReleaseChannel.MANUAL
                    operatorName = "模拟运营员"
                    carOwnerName = "模拟车辆联系人"
                    gateName = "模拟东门道闸"
                    feeAmount = BigDecimal("5.00")
                    vehicleTypeName = carType.carName
                }
            },
            accessRecordRepository::save,
        )
        ensure(
            accessRecordRepository.findByIdentity("粤A·MOCK02", AccessRecord.InAndOut.IN, now.minusHours(5))
                ?: accessRecordRepository.findFirstByCarNumberAndInAndOutOrderByInAndOutTimeDesc("粤A·MOCK02", AccessRecord.InAndOut.IN),
            {
                AccessRecord().apply {
                    carNumber = "粤A·MOCK02"
                    inAndOut = AccessRecord.InAndOut.IN
                    inAndOutTime = now.minusHours(5)
                    this.carType = suvCarType
                    departmentName = propertyDepartment.name
                    releaseChannel = AccessRecord.ReleaseChannel.AUTOMATIC
                    carOwnerName = "模拟企业车主"
                    gateName = "模拟西门道闸"
                    vehicleTypeName = suvCarType.carName
                    recordStatus = "正常"
                }
            },
            accessRecordRepository::save,
        )
        ensure(
            accessRecordRepository.findByIdentity("粤A·MOCK02", AccessRecord.InAndOut.OUT, now.minusHours(3))
                ?: accessRecordRepository.findFirstByCarNumberAndInAndOutOrderByInAndOutTimeDesc("粤A·MOCK02", AccessRecord.InAndOut.OUT),
            {
                AccessRecord().apply {
                    carNumber = "粤A·MOCK02"
                    inAndOut = AccessRecord.InAndOut.OUT
                    inAndOutTime = now.minusHours(3)
                    this.carType = suvCarType
                    departmentName = propertyDepartment.name
                    releaseChannel = AccessRecord.ReleaseChannel.REMOTE
                    releaseInstructions = "模拟远程确认放行"
                    operatorName = "模拟物业专员"
                    carOwnerName = "模拟企业车主"
                    gateName = "模拟西门道闸"
                    feeAmount = BigDecimal("2.50")
                    vehicleTypeName = suvCarType.carName
                }
            },
            accessRecordRepository::save,
        )
        ensure(
            accessRecordRepository.findByIdentity("粤A·MOCK03", AccessRecord.InAndOut.IN, now.minusDays(1).withHour(8))
                ?: accessRecordRepository.findFirstByCarNumberAndInAndOutOrderByInAndOutTimeDesc("粤A·MOCK03", AccessRecord.InAndOut.IN),
            {
                AccessRecord().apply {
                    carNumber = "粤A·MOCK03"
                    inAndOut = AccessRecord.InAndOut.IN
                    inAndOutTime = now.minusDays(1).withHour(8)
                    this.carType = truckCarType
                    departmentName = securityDepartment.name
                    releaseChannel = AccessRecord.ReleaseChannel.MANUAL
                    releaseInstructions = "模拟配送车辆登记"
                    operatorName = "模拟安全主管"
                    carOwnerName = "模拟配送车主"
                    gateName = "模拟西门道闸"
                    vehicleTypeName = truckCarType.carName
                    recordStatus = "异常"
                }
            },
            accessRecordRepository::save,
        )

        logger.info(
            "模拟数据写入完成: 部门=4, 用户=3, 设备=4, 区域=3, 车辆主档=3, 车主=3, 车位=5, 车牌=3, 门禁人员=2, 门禁授权=3, 人员进出记录=3, 车辆进出记录=5, 违规记录=1",
        )
    }

    private fun <T : Any> ensure(
        existing: T?,
        factory: () -> T,
        save: (T) -> T,
    ): T = existing ?: save(factory())
}
