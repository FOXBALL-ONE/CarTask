package top.foxball.cartask.controller

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import top.foxball.cartask.audit.AuditService
import top.foxball.cartask.authentication.CurrentUserPrincipal
import top.foxball.cartask.entity.ViolationRecord
import top.foxball.cartask.entity.ViolationSubject
import top.foxball.cartask.entity.type.ViolationType
import top.foxball.cartask.repository.ViolationRecordRepository
import top.foxball.cartask.repository.ViolationSubjectRepository
import top.foxball.cartask.repository.ViolationTypeRepository
import top.foxball.cartask.scope.WithCurrentUser
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@SpringBootTest(
    properties = [
        "app.mock-data.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:wx_violation_notice_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    ],
)
@ActiveProfiles("test")
class WxViolationNoticeControllerIntegrationTests(
    @Autowired private val controller: WxViolationNoticeController,
    @Autowired private val recordRepository: ViolationRecordRepository,
    @Autowired private val subjectRepository: ViolationSubjectRepository,
    @Autowired private val typeRepository: ViolationTypeRepository,
) {
    @MockitoBean
    lateinit var auditService: AuditService

    @BeforeEach
    fun clearRecords() {
        recordRepository.deleteAll()
        subjectRepository.deleteAll()
        typeRepository.deleteAll()
    }

    @Test
    @WithCurrentUser(role = "SUPER_ADMIN")
    fun `通报列表只返回已确认或已处理记录`() {
        val type = typeRepository.save(ViolationType().apply {
            violationName = "占用消防通道"
            violationFraction = 6
            violationContent = "车辆占用消防通道"
        })
        val subject = subjectRepository.save(ViolationSubject().apply {
            subjectType = ViolationSubject.SubjectType.VEHICLE
            subjectName = "测试车主"
            subjectNumber = "闽A90001"
        })
        fun saveRecord(status: ViolationRecord.HandlingStatus, day: Int) = recordRepository.save(
            ViolationRecord().apply {
                this.subject = subject
                violationType = type
                violationTime = LocalDateTime.of(2026, 10, day, 10, 0)
                violationContent = "车辆占用消防通道"
                violationFraction = 6
                handlingStatus = status
            },
        )

        val confirmed = saveRecord(ViolationRecord.HandlingStatus.CONFIRMED, 15)
        val handled = saveRecord(ViolationRecord.HandlingStatus.HANDLED, 16)
        val pending = saveRecord(ViolationRecord.HandlingStatus.PENDING, 17)
        saveRecord(ViolationRecord.HandlingStatus.CANCELLED, 18)
        val principal = CurrentUserPrincipal(1, "reviewer", "SUPER_ADMIN", "notice-token")

        val page = controller.listNotices(principal, 1, 10).body!!.data!!
        val items = page.javaClass.getMethod("getItems").invoke(page) as List<*>
        val ids = items.map { it!!.javaClass.getMethod("getId").invoke(it) }

        assertEquals(2, page.javaClass.getMethod("getTotal").invoke(page))
        assertEquals(listOf(handled.id, confirmed.id), ids)
        assertFailsWith<IllegalArgumentException> { controller.getNotice(principal, requireNotNull(pending.id)) }
    }
}
