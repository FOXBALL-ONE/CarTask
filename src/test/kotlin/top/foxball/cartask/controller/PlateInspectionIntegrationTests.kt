package top.foxball.cartask.controller

import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import top.foxball.cartask.audit.AuditService
import top.foxball.cartask.entity.ParkingOwner
import top.foxball.cartask.entity.ParkingPlate
import top.foxball.cartask.repository.ParkingOwnerRepository
import top.foxball.cartask.repository.ParkingPlateRepository
import top.foxball.cartask.scope.WithCurrentUser
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest(properties = [
    "app.mock-data.enabled=false",
    "spring.datasource.url=jdbc:h2:mem:plate_inspection_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
])
@ActiveProfiles("test")
class PlateInspectionIntegrationTests(
    @Autowired private val controller: ExcelController,
    @Autowired private val api: ParkingApiController,
    @Autowired private val owners: ParkingOwnerRepository,
    @Autowired private val plates: ParkingPlateRepository,
    @Autowired private val objectMapper: ObjectMapper,
) {
    @MockitoBean
    lateinit var auditService: AuditService

    @Test
    @WithCurrentUser(role = "ADMIN", authorities = ["plate:manage", "plate:read"])
    fun `导入的年检信息按车牌号落到已有车辆并补全年检有效期`() {
        val owner = saveOwner("CARD-1001", "张伟")
        savePlate("京A12345", owner)
        savePlate("京B00001", owner)

        val file = workbook(listOf(
            listOf("京A12345", "是", "2026-05-20", "", "上线检验"),
            listOf("京B00001", "是", "2026-05-20", "2028-05-19", "按表里填的有效期"),
        ))
        val result = controller.import("plate-inspections", MockMultipartFile("file", "inspection.xlsx", null, file))

        assertEquals(201, result.statusCode.value())
        val renewed = plates.findByPlate("京A12345")!!
        assertEquals(LocalDate.parse("2026-05-20"), renewed.inspectionDate)
        assertEquals(LocalDate.parse("2027-05-20"), renewed.inspectionValidUntil)
        assertEquals("上线检验", renewed.inspectionRemark)
        val explicit = plates.findByPlate("京B00001")!!
        assertEquals(LocalDate.parse("2028-05-19"), explicit.inspectionValidUntil)
    }

    @Test
    @WithCurrentUser(role = "ADMIN", authorities = ["plate:manage", "plate:read"])
    fun `未年检的行会清空原有的年检登记`() {
        val owner = saveOwner("CARD-1002", "李四")
        val plate = savePlate("京C00002", owner)
        plate.inspectionDate = LocalDate.parse("2025-03-01")
        plate.inspectionValidUntil = LocalDate.parse("2026-03-01")
        plate.inspectionRemark = "旧记录"
        plates.save(plate)

        val file = workbook(listOf(listOf("京C00002", "否", "", "", "")))
        controller.import("plate-inspections", MockMultipartFile("file", "clear.xlsx", null, file))

        val cleared = plates.findByPlate("京C00002")!!
        assertNull(cleared.inspectionDate)
        assertNull(cleared.inspectionValidUntil)
        assertNull(cleared.inspectionRemark)
    }

    @Test
    @WithCurrentUser(role = "ADMIN", authorities = ["plate:manage", "plate:read"])
    fun `车牌号缺失重复或未填年检标记时整批拒绝`() {
        val owner = saveOwner("CARD-1003", "王五")
        savePlate("京D00003", owner)

        assertTrue(importError(listOf(listOf("京D00003", "是", "2026-05-20", "", ""), listOf("京E00004", "是", "2026-05-20", "", "")))
            .contains("车牌号不存在"))
        assertTrue(importError(listOf(listOf("京D00003", "是", "2026-05-20", "", ""), listOf("京D00003", "是", "2026-05-21", "", "")))
            .contains("车牌号重复"))
        assertTrue(importError(listOf(listOf("京D00003", "", "2026-05-20", "", "")))
            .contains("是否已年检不能为空"))
        assertTrue(importError(listOf(listOf("京D00003", "是", "2026-05-20", "2026-05-19", "")))
            .contains("年检有效期不能早于年检日期"))
        assertNull(plates.findByPlate("京D00003")!!.inspectionDate)

        savePlate("京K00009", owner)
        savePlate("京K·00009", owner)
        assertTrue(importError(listOf(listOf("京K00009", "是", "2026-05-20", "", "")))
            .contains("请先在车牌信息中去重"))
        assertNull(plates.findByPlate("京K00009")!!.inspectionDate)
    }

    @Test
    @WithCurrentUser(role = "ADMIN", authorities = ["plate:manage", "plate:read"])
    fun `模板与导出共用同一套年检列`() {
        val owner = saveOwner("CARD-1004", "赵六")
        val plate = savePlate("京F00005", owner)
        plate.inspectionDate = LocalDate.parse("2026-01-10")
        plate.inspectionValidUntil = LocalDate.parse("2027-01-10")
        plates.save(plate)

        XSSFWorkbook(controller.template("plate-inspections").body!!.byteArray.inputStream()).use { workbook ->
            val header = workbook.getSheet("数据").getRow(0)
            assertEquals(listOf("车牌号", "是否已年检", "年检日期", "年检有效期至", "备注"), (0..4).map { cellText(header, it) })
        }
        XSSFWorkbook(controller.export("plate-inspections").body!!.byteArray.inputStream()).use { workbook ->
            val sheet = workbook.getSheet("数据")
            val row = sheet.asSequence().first { cellText(it, 1) == "京F00005" }
            assertEquals("有效", cellText(row, 4))
            assertEquals("是", cellText(row, 5))
            assertEquals("2026-01-10", cellText(row, 6))
            assertEquals("2027-01-10", cellText(row, 7))
        }
    }

    @Test
    @WithCurrentUser(role = "ADMIN", authorities = ["plate:manage", "plate:read"])
    fun `登记年检只给已年检标记时按今天起算一年有效期`() {
        val owner = saveOwner("CARD-1005", "钱七")
        val plate = savePlate("京G00006", owner)

        api.updatePlate(requireNotNull(plate.id), PlateRequest(inspected = true))

        val renewed = plates.findByPlate("京G00006")!!
        assertEquals(LocalDate.now(), renewed.inspectionDate)
        assertEquals(LocalDate.now().plusYears(1), renewed.inspectionValidUntil)

        val created = api.createPlate(PlateRequest(
            plate = "京J00008", owner = owner.name, ownerId = owner.id, status = 1, regDate = "2026-09-01",
            inspected = true, inspectionDate = "2026-08-15",
        ))
        assertEquals(201, created.statusCode.value())
        val saved = plates.findByPlate("京J00008")!!
        assertEquals(LocalDate.parse("2026-08-15"), saved.inspectionDate)
        assertEquals(LocalDate.parse("2027-08-15"), saved.inspectionValidUntil)
    }

    @Test
    @WithCurrentUser(role = "ADMIN", authorities = ["plate:manage", "plate:read"])
    fun `改车牌号或状态不会顺手抹掉已有的年检登记`() {
        val owner = saveOwner("CARD-1006", "孙八")
        val plate = savePlate("京H00007", owner)
        plate.inspectionDate = LocalDate.parse("2026-02-01")
        plate.inspectionValidUntil = LocalDate.parse("2027-02-01")
        plates.save(plate)

        api.updatePlate(requireNotNull(plate.id), PlateRequest(status = 0))

        val updated = plates.findByPlate("京H00007")!!
        assertEquals(0, updated.status)
        assertEquals(LocalDate.parse("2026-02-01"), updated.inspectionDate)
        assertEquals(LocalDate.parse("2027-02-01"), updated.inspectionValidUntil)

        api.updatePlate(requireNotNull(plate.id), PlateRequest(inspected = false))

        val cleared = plates.findByPlate("京H00007")!!
        assertNull(cleared.inspectionDate)
        assertNull(cleared.inspectionValidUntil)
    }

    @Test
    @WithCurrentUser(role = "ADMIN", authorities = ["plate:manage", "plate:read"])
    fun `车牌接口支持保存更新并返回车辆类型`() {
        val owner = saveOwner("CARD-1006-BRAND", "孙八车辆类型")
        val created = api.createPlate(PlateRequest(
            plate = "京HBRAND", owner = owner.name, ownerId = owner.id, status = 1, regDate = "2026-09-01",
            carBrand = "  小型轿车  ",
        ))
        assertEquals(201, created.statusCode.value())
        assertEquals("小型轿车", objectMapper.valueToTree<JsonNode>(created.body!!.data).get("carBrand").asText())

        val plate = plates.findByPlate("京HBRAND")!!
        assertEquals("小型轿车", plate.carBrand)

        api.updatePlate(requireNotNull(plate.id), PlateRequest(carBrand = "SUV"))

        assertEquals("SUV", plates.findByPlate("京HBRAND")!!.carBrand)
        assertEquals("SUV", listPlates(null).single { it.get("plate").asText() == "京HBRAND" }.get("carBrand").asText())
    }

    @Test
    @WithCurrentUser(role = "ADMIN", authorities = ["plate:read"])
    fun `车牌列表返回年检字段并支持按年检状态筛选`() {
        val owner = saveOwner("CARD-1007", "周九")
        val valid = savePlate("京L00010", owner)
        valid.inspectionDate = LocalDate.now().minusMonths(1)
        valid.inspectionValidUntil = LocalDate.now().plusMonths(1)
        valid.inspectionRemark = "即将到期"
        plates.save(valid)
        val expired = savePlate("京L00011", owner)
        expired.inspectionDate = LocalDate.now().minusYears(2)
        expired.inspectionValidUntil = LocalDate.now().minusYears(1)
        plates.save(expired)
        savePlate("京L00012", owner)

        val items = listPlates("已过期")
        assertEquals(listOf("京L00011"), items.map { it.get("plate").asText() })
        assertEquals("已过期", items.single().get("inspectionStatus").asText())
        val renewed = listPlates("有效").single { it.get("plate").asText() == "京L00010" }
        assertEquals("即将到期", renewed.get("inspectionRemark").asText())
        assertEquals(LocalDate.now().plusMonths(1).toString(), renewed.get("inspectionValidUntil").asText())
        assertEquals("未年检", listPlates("未年检").single { it.get("plate").asText() == "京L00012" }.get("inspectionStatus").asText())
    }

    
    private fun listPlates(inspectionStatus: String?): List<JsonNode> = objectMapper
        .valueToTree<JsonNode>(api.listPlates(null, null, inspectionStatus, 1, 100).body!!.data)
        .get("items")
        .values()
        .toList()

    
    private fun workbook(rows: List<List<String>>): ByteArray {
        val headers = listOf("车牌号", "是否已年检", "年检日期", "年检有效期至", "备注")
        val output = ByteArrayOutputStream()
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("数据")
            sheet.createRow(0).also { header -> headers.forEachIndexed { index, name -> header.createCell(index).setCellValue(name) } }
            rows.forEachIndexed { index, values ->
                sheet.createRow(index + 1).also { row -> values.forEachIndexed { cell, value -> row.createCell(cell).setCellValue(value) } }
            }
            workbook.write(output)
        }
        return output.toByteArray()
    }

    private fun importError(rows: List<List<String>>): String = assertFailsWith<IllegalArgumentException> {
        controller.import("plate-inspections", MockMultipartFile("file", "invalid.xlsx", null, workbook(rows)))
    }.message!!

    private fun cellText(row: Row, index: Int): String = DataFormatter().formatCellValue(row.getCell(index))

    private fun saveOwner(cardId: String, name: String): ParkingOwner = owners.save(ParkingOwner().apply {
        this.cardId = cardId
        this.name = name
        dept = "示例运营部"
        phone = "13800000000"
    })

    private fun savePlate(number: String, owner: ParkingOwner): ParkingPlate {
        val now = LocalDateTime.now()
        return plates.save(ParkingPlate().apply {
            plate = number
            this.owner = owner.name
            ownerId = requireNotNull(owner.id)
            status = 1
            regDate = LocalDate.parse("2024-01-15")
            createdAt = now
            updatedAt = now
        })
    }
}
