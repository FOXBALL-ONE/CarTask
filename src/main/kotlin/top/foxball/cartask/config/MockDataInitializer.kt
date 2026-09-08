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
import top.foxball.cartask.entity.Position
import top.foxball.cartask.entity.Role
import top.foxball.cartask.entity.User
import top.foxball.cartask.entity.type.AccessControlType
import top.foxball.cartask.entity.type.CarType
import top.foxball.cartask.entity.type.LicensePlateType
import top.foxball.cartask.entity.type.ReleaseType
import top.foxball.cartask.entity.type.RestrictionType
import top.foxball.cartask.entity.type.ZoneType
import top.foxball.cartask.repository.AccessControlRepository
import top.foxball.cartask.repository.AccessControlTypeRepository
import top.foxball.cartask.repository.AccessRecordRepository
import top.foxball.cartask.repository.CarMasterInfoRepository
import top.foxball.cartask.repository.CarTypeRepository
import top.foxball.cartask.repository.DepartmentRepository
import top.foxball.cartask.repository.DeviceRepository
import top.foxball.cartask.repository.LicensePlateTypeRepository
import top.foxball.cartask.repository.PositionRepository
import top.foxball.cartask.repository.RoleRepository
import top.foxball.cartask.repository.ReleaseTypeRepository
import top.foxball.cartask.repository.RestrictionTypeRepository
import top.foxball.cartask.repository.UserRepository
import top.foxball.cartask.repository.ZoneTypeRepository
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
        ensure(
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
                    orderNumber = 1
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
            restrictionTypeRepository.findFirstByRestrictionName("模拟临时限制"),
            {
                RestrictionType().apply {
                    restrictionName = "模拟临时限制"
                    orderNumber = 1
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
                        parkingSpaceCount = 1
                        locationInfo = "A-001"
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
            accessRecordRepository.findByIdentity("粤A·MOCK01", AccessRecord.InAndOut.IN, now.minusHours(2))
                ?: accessRecordRepository.findFirstByCarNumberAndInAndOutOrderByInAndOutTimeDesc("粤A·MOCK01", AccessRecord.InAndOut.IN),
            {
                AccessRecord().apply {
                    carNumber = "粤A·MOCK01"
                    inAndOut = AccessRecord.InAndOut.IN
                    inAndOutTime = now.minusHours(2)
                    this.carType = carType
                    releaseChannel = AccessRecord.ReleaseChannel.AUTOMATIC
                    carOwnerName = "模拟车辆联系人"
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
                    releaseInstructions = "模拟授权放行"
                    releaseChannel = AccessRecord.ReleaseChannel.MANUAL
                    operatorName = "模拟运营员"
                    carOwnerName = "模拟车辆联系人"
                }
            },
            accessRecordRepository::save,
        )

        logger.info("模拟数据写入完成: 区域={}, 车辆类型={}, 门禁授权={}, 进出记录=2", zone.zoneCode, carType.carName, accessControlType.accessControlName)
    }

    private fun <T : Any> ensure(
        existing: T?,
        factory: () -> T,
        save: (T) -> T,
    ): T = existing ?: save(factory())
}
