package top.foxball.cartask.controller

import java.time.LocalDateTime
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import top.foxball.cartask.entity.ViolationSubject
import top.foxball.cartask.repository.ViolationRecordRepository
import top.foxball.cartask.repository.ViolationSettingRepository
import top.foxball.cartask.repository.ViolationSubjectRepository
import top.foxball.cartask.repository.ViolationTypeRepository
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
    @BeforeEach
    fun clearData() {
        violationRecordRepository.deleteAll()
        violationSubjectRepository.deleteAll()
        violationTypeRepository.deleteAll()
        violationSettingRepository.deleteAll()
    }

    @Test
    @WithMockUser(authorities = ["violation:read", "violation:manage"])
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
}
