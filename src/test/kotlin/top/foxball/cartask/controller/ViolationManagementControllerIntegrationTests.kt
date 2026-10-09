package top.foxball.cartask.controller

import java.time.LocalDateTime
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import top.foxball.cartask.audit.AuditService
import top.foxball.cartask.scope.WithCurrentUser
import top.foxball.cartask.entity.ViolationSubject
import top.foxball.cartask.repository.ViolationRecordRepository
import top.foxball.cartask.repository.ViolationSettingRepository
import top.foxball.cartask.repository.ViolationSubjectRepository
import top.foxball.cartask.repository.ViolationTypeRepository
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest(
    properties = [
        "app.mock-data.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:violation_management_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    ],
)
@ActiveProfiles("test")
class ViolationManagementControllerIntegrationTests(
    @Autowired private val controller: ViolationManagementController,
    @Autowired private val violationRecordRepository: ViolationRecordRepository,
    @Autowired private val violationSubjectRepository: ViolationSubjectRepository,
    @Autowired private val violationTypeRepository: ViolationTypeRepository,
    @Autowired private val violationSettingRepository: ViolationSettingRepository,
) {
    /** 审计写入用了 PostgreSQL 专有的 pg_advisory_xact_lock，H2 下必须整体替换掉。 */
    @MockitoBean
    lateinit var auditService: AuditService

    @BeforeEach
    fun clearData() {
        violationRecordRepository.deleteAll()
        violationSubjectRepository.deleteAll()
        violationTypeRepository.deleteAll()
        violationSettingRepository.deleteAll()
    }

    @Test
    @WithCurrentUser(role = "ADMIN", authorities = ["violation:read", "violation:manage"])
    fun `确认违规后按阈值处罚且规则变更不改历史分值`() {
        controller.updateViolationSetting(6, 30)
        controller.createViolationType("堵塞消防通道", 6, "车辆占用消防通道", 1, 1)
        val type = requireNotNull(violationTypeRepository.findFirstByViolationName("堵塞消防通道"))
        controller.createViolation(
            "粤a12345",
            "测试车主",
            requireNotNull(type.id),
            LocalDateTime.of(2026, 9, 9, 9, 30),
            "一号车场",
            "https://example.com/evidence.jpg",
        )
        val record = violationRecordRepository.findAllWithViolationType().single()

        controller.handleViolation(requireNotNull(record.id), "HANDLED", "admin", "现场证据已核验")

        assertEquals(ViolationSubject.Status.BANNED, violationSubjectRepository.findBySubjectNumber("粤A12345")?.status)
        assertEquals(6, violationRecordRepository.findById(requireNotNull(record.id)).orElseThrow().violationFraction)
        controller.updateViolationType(requireNotNull(type.id), "堵塞消防通道", 3, "调整后的规则", 1, 1)
        assertEquals(6, violationRecordRepository.findById(requireNotNull(record.id)).orElseThrow().violationFraction)
        assertEquals(200, controller.listViolationPenalties(null, null, null, null, 1, 10).statusCode.value())
        assertFailsWith<IllegalArgumentException> { controller.deleteViolationType(requireNotNull(type.id)) }

        val subject = requireNotNull(violationSubjectRepository.findBySubjectNumber("粤A12345"))
        controller.releaseViolationPenalty(requireNotNull(subject.id), "人工复核后解除")
        assertEquals(ViolationSubject.Status.Activity, violationSubjectRepository.findById(requireNotNull(subject.id)).orElseThrow().status)
    }

    @Test
    @WithCurrentUser(role = "ADMIN", authorities = ["violation:manage"])
    fun `上传的证据图片存成下载地址而不是原始文件名`() {
        controller.createViolationType("占用消防通道", 6, "车辆占用消防通道", 1, 1)
        val type = requireNotNull(violationTypeRepository.findFirstByViolationName("占用消防通道"))

        controller.createViolationWithEvidence(
            "粤B00001",
            "测试车主",
            requireNotNull(type.id),
            LocalDateTime.of(2026, 10, 3, 10, 0),
            "二号车场",
            MockMultipartFile("evidence", "现场照片.png", "image/png", PNG_BYTES),
        )

        val evidence = requireNotNull(violationRecordRepository.findAllWithViolationType().single().evidenceInfo)
        assertTrue(Regex(".*/api/files/[0-9a-fA-F-]{36}/download$").matches(evidence), "证据地址不是下载地址：$evidence")
    }

    @Test
    @WithCurrentUser(role = "ADMIN", authorities = ["violation:manage"])
    fun `内容不是图片的文件无法作为证据上传`() {
        controller.createViolationType("逆行", 3, "车辆逆行", 1, 1)
        val type = requireNotNull(violationTypeRepository.findFirstByViolationName("逆行"))

        assertFailsWith<IllegalArgumentException> {
            controller.createViolationWithEvidence(
                "粤B00002",
                "测试车主",
                requireNotNull(type.id),
                LocalDateTime.of(2026, 10, 3, 10, 0),
                null,
                MockMultipartFile("evidence", "恶意文件.png", "image/png", "#!/bin/sh".toByteArray()),
            )
        }
        assertEquals(emptyList(), violationRecordRepository.findAllWithViolationType())
    }

    private companion object {
        /** 只校验文件头，一段合法的 PNG 签名就足够走通上传链路。 */
        val PNG_BYTES = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D, 0x0A, 0x1A, 0x0A)
    }

    /**
     * 同一个路径上挂了两个 POST /violations（普通表单参数版和多部件上传版），
     * 只有真正走一次分发才能确认 multipart 请求命中的是上传版、普通请求仍走原方法。
     */
    @Test
    @WithCurrentUser(role = "ADMIN", authorities = ["violation:manage"])
    fun `多部件请求命中上传接口而带参数请求仍走原接口`() {
        controller.createViolationType("违停", 3, "违规停放", 1, 1)
        val type = requireNotNull(violationTypeRepository.findFirstByViolationName("违停"))
        val mvc = MockMvcBuilders.standaloneSetup(controller).build()

        mvc.perform(
            multipart("/api/violations")
                .file(MockMultipartFile("evidence", "现场照片.png", "image/png", PNG_BYTES))
                .param("subject_number", "粤C00001")
                .param("subject_name", "测试车主")
                .param("type_id", requireNotNull(type.id).toString())
                .param("violation_time", "2026-10-03T10:00:00")
                .param("location", "三号车场"),
        ).andExpect(status().isCreated)

        val uploaded = requireNotNull(evidenceOf("粤C00001"))
        assertTrue(Regex(".*/api/files/[0-9a-fA-F-]{36}/download$").matches(uploaded), "多部件请求没有走到上传接口：$uploaded")

        // 证据图片选填：不带文件的多部件表单同样要能落库（选择器返回 null 而不是报缺部件）。
        mvc.perform(
            multipart("/api/violations")
                .param("subject_number", "粤C00003")
                .param("subject_name", "测试车主")
                .param("type_id", requireNotNull(type.id).toString())
                .param("violation_time", "2026-10-03T12:00:00"),
        ).andExpect(status().isCreated)

        assertNull(evidenceOf("粤C00003"))

        mvc.perform(
            post("/api/violations")
                .param("subject_number", "粤C00002")
                .param("subject_name", "测试车主")
                .param("type_id", requireNotNull(type.id).toString())
                .param("violation_time", "2026-10-03T11:00:00")
                .param("evidence_info", "https://example.com/evidence.jpg"),
        ).andExpect(status().isCreated)

        assertEquals("https://example.com/evidence.jpg", evidenceOf("粤C00002"))
    }

    private fun evidenceOf(subjectNumber: String): String? =
        violationRecordRepository.findAllWithViolationType()
            .single { it.subject.subjectNumber == subjectNumber }
            .evidenceInfo
}
