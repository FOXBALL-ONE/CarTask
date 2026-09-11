package top.foxball.cartask.config

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import top.foxball.cartask.repository.AccessControlRepository
import top.foxball.cartask.repository.AccessRecordRepository
import top.foxball.cartask.repository.CarMasterInfoRepository
import top.foxball.cartask.repository.DeviceRepository
import top.foxball.cartask.repository.GateDeleteRequestRepository
import top.foxball.cartask.repository.GatePersonRepository
import top.foxball.cartask.repository.ParkingOwnerRepository
import top.foxball.cartask.repository.ParkingPlateRepository
import top.foxball.cartask.repository.ParkingSpotRepository
import top.foxball.cartask.repository.PersonAccessRecordRepository
import top.foxball.cartask.repository.UserRepository
import top.foxball.cartask.repository.ViolationRecordRepository
import kotlin.test.Test
import kotlin.test.assertEquals

@SpringBootTest(
    properties = [
        "app.mock-data.enabled=true",
        "app.mock-data.reference-time=2026-09-08T10:00:00",
        "spring.datasource.url=jdbc:h2:mem:mock_data_initializer_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    ],
)
@ActiveProfiles("test")
class MockDataInitializerIntegrationTests(
    @Autowired private val initializer: MockDataInitializer,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val deviceRepository: DeviceRepository,
    @Autowired private val carMasterInfoRepository: CarMasterInfoRepository,
    @Autowired private val parkingOwnerRepository: ParkingOwnerRepository,
    @Autowired private val parkingSpotRepository: ParkingSpotRepository,
    @Autowired private val parkingPlateRepository: ParkingPlateRepository,
    @Autowired private val gatePersonRepository: GatePersonRepository,
    @Autowired private val gateDeleteRequestRepository: GateDeleteRequestRepository,
    @Autowired private val personAccessRecordRepository: PersonAccessRecordRepository,
    @Autowired private val accessControlRepository: AccessControlRepository,
    @Autowired private val accessRecordRepository: AccessRecordRepository,
    @Autowired private val violationRecordRepository: ViolationRecordRepository,
) {
    @Test
    fun `写入完整模拟数据且重复执行不产生重复记录`() {
        assertCounts()

        initializer.write()

        assertCounts()
    }

    private fun assertCounts() {
        assertEquals(3, userRepository.count())
        assertEquals(4, deviceRepository.count())
        assertEquals(3, carMasterInfoRepository.count())
        assertEquals(3, parkingOwnerRepository.count())
        assertEquals(5, parkingSpotRepository.count())
        assertEquals(3, parkingPlateRepository.count())
        assertEquals(2, gatePersonRepository.count())
        assertEquals(1, gateDeleteRequestRepository.count())
        assertEquals(3, personAccessRecordRepository.count())
        assertEquals(3, accessControlRepository.count())
        assertEquals(5, accessRecordRepository.count())
        assertEquals(1, violationRecordRepository.count())
    }
}
