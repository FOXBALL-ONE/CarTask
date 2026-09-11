package top.foxball.cartask.controller

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import top.foxball.cartask.audit.AuditService
import top.foxball.cartask.repository.*
import java.io.ByteArrayOutputStream
import kotlin.test.*

@SpringBootTest(properties = [
    "app.mock-data.enabled=false",
    "spring.datasource.url=jdbc:h2:mem:excel_import_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
])
@ActiveProfiles("test")
class ExcelImportIntegrationTests(
    @Autowired private val controller: ExcelController,
    @Autowired private val departments: DepartmentRepository,
    @Autowired private val positions: PositionRepository,
    @Autowired private val users: UserRepository,
    @Autowired private val owners: ParkingOwnerRepository,
    @Autowired private val spots: ParkingSpotRepository,
    @Autowired private val plates: ParkingPlateRepository,
    @Autowired private val devices: DeviceRepository,
    @Autowired private val persons: GatePersonRepository,
) {
    @MockitoBean
    lateinit var auditService: AuditService

    @Test
    @WithMockUser(authorities = ["ROLE_SUPER_ADMIN", "department:manage", "user:create", "position:manage",
        "owner:manage", "spot:manage", "plate:manage", "device:manage", "gate-person:manage", "user:role-assign"])
    fun `sample imports dependencies and late errors roll back earlier sheets`() {
        val sample = controller.allTemplate().body!!.byteArray
        val missing = ByteArrayOutputStream()
        XSSFWorkbook(sample.inputStream()).use { workbook ->
            workbook.removeSheetAt(workbook.getSheetIndex("车牌"))
            workbook.write(missing)
        }
        val missingError = assertFailsWith<IllegalArgumentException> {
            controller.import("all", MockMultipartFile("file", "missing.xlsx", null, missing.toByteArray()))
        }
        assertTrue(missingError.message!!.contains("车牌"))

        val cyclic = ByteArrayOutputStream()
        XSSFWorkbook(sample.inputStream()).use { workbook ->
            workbook.getSheet("部门").getRow(1).getCell(2).setCellValue("SAMPLE_DEPT")
            workbook.write(cyclic)
        }
        val cycleError = assertFailsWith<IllegalArgumentException> {
            controller.import("all", MockMultipartFile("file", "cycle.xlsx", null, cyclic.toByteArray()))
        }
        assertTrue(cycleError.message!!.contains("循环引用"))

        val broken = ByteArrayOutputStream()
        XSSFWorkbook(sample.inputStream()).use { workbook ->
            assertEquals(9, workbook.numberOfSheets)
            workbook.getSheet("设备").getRow(1).getCell(8).setCellValue("无效状态")
            workbook.write(broken)
        }
        assertFailsWith<IllegalArgumentException> {
            controller.import("all", MockMultipartFile("file", "broken.xlsx", null, broken.toByteArray()))
        }
        assertFalse(departments.findAll().any { it.departmentNumber == "SAMPLE_ROOT" })
        assertNull(positions.findByCodeNumber("SAMPLE_POST"))
        assertFalse(users.existsByUsername("sample_user"))
        assertFalse(owners.existsByCardId("SAMPLE_CARD"))
        assertFalse(spots.existsByCode("SAMPLE_SPOT"))
        assertFalse(plates.existsByPlate("京A12345"))

        val result = controller.import("all", MockMultipartFile("file", "sample.xlsx", null, sample))
        assertEquals(201, result.statusCode.value())
        val department = departments.findAll().single { it.departmentNumber == "SAMPLE_DEPT" }
        val position = positions.findByCodeNumber("SAMPLE_POST")!!
        val user = users.findAll().single { it.username == "sample_user" }
        assertEquals(department.id, user.department!!.id)
        assertEquals(position.id, user.position!!.id)
        assertNotEquals("ReplaceMe!2026", user.passwordHash)
        val owner = owners.findAll().single { it.cardId == "SAMPLE_CARD" }
        val plate = plates.findAll().single { it.plate == "京A12345" }
        assertEquals(owner.id, plate.ownerId)
        assertEquals(owner.name, plate.owner)
        assertEquals(1, owner.plateCount)
        assertEquals(1, owner.spotCount)
        assertNotNull(devices.findByDeviceCode("SAMPLE_DEVICE"))
        assertTrue(persons.existsByCode("SAMPLE_PERSON"))

        assertFailsWith<IllegalArgumentException> {
            controller.import("all", MockMultipartFile("file", "duplicate.xlsx", null, sample))
        }
        assertEquals(1, owners.findAll().count { it.cardId == "SAMPLE_CARD" })
    }
}
