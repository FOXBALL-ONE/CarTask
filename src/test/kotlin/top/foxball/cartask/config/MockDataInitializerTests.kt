package top.foxball.cartask.config

import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.security.crypto.password.PasswordEncoder
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

class MockDataInitializerTests {
    private val passwordEncoder = mock<PasswordEncoder>()
    private val departmentRepository = mock<DepartmentRepository>()
    private val positionRepository = mock<PositionRepository>()
    private val userRepository = mock<UserRepository>()
    private val roleRepository = mock<RoleRepository>()
    private val deviceRepository = mock<DeviceRepository>()
    private val carTypeRepository = mock<CarTypeRepository>()
    private val licensePlateTypeRepository = mock<LicensePlateTypeRepository>()
    private val accessControlTypeRepository = mock<AccessControlTypeRepository>()
    private val releaseTypeRepository = mock<ReleaseTypeRepository>()
    private val restrictionTypeRepository = mock<RestrictionTypeRepository>()
    private val zoneTypeRepository = mock<ZoneTypeRepository>()
    private val carMasterInfoRepository = mock<CarMasterInfoRepository>()
    private val accessControlRepository = mock<AccessControlRepository>()
    private val accessRecordRepository = mock<AccessRecordRepository>()

    private val initializer = MockDataInitializer(
        MockDataProperties(enabled = false),
        passwordEncoder,
        departmentRepository,
        positionRepository,
        userRepository,
        roleRepository,
        deviceRepository,
        carTypeRepository,
        licensePlateTypeRepository,
        accessControlTypeRepository,
        releaseTypeRepository,
        restrictionTypeRepository,
        zoneTypeRepository,
        carMasterInfoRepository,
        accessControlRepository,
        accessRecordRepository,
    )

    @Test
    fun `开关关闭时不装配初始化器`() {
        val condition = requireNotNull(MockDataInitializer::class.java.getAnnotation(ConditionalOnProperty::class.java))

        kotlin.test.assertEquals("app.mock-data", condition.prefix)
        kotlin.test.assertEquals(listOf("enabled"), condition.name.toList())
        kotlin.test.assertEquals("true", condition.havingValue)
    }

    @Test
    fun `写入方法监听应用就绪事件`() {
        val method = MockDataInitializer::class.java.getDeclaredMethod("write")

        val listener = requireNotNull(method.getAnnotation(EventListener::class.java))
        kotlin.test.assertEquals(
            ApplicationReadyEvent::class,
            listener.classes.single(),
        )
    }

    @Test
    fun `模拟数据开关关闭时不访问数据库`() {
        initializer.write()

        verifyNoInteractions(
            passwordEncoder,
            departmentRepository,
            positionRepository,
            userRepository,
            roleRepository,
            deviceRepository,
            carTypeRepository,
            licensePlateTypeRepository,
            accessControlTypeRepository,
            releaseTypeRepository,
            restrictionTypeRepository,
            zoneTypeRepository,
            carMasterInfoRepository,
            accessControlRepository,
            accessRecordRepository,
        )
    }
}
