package top.foxball.cartask.audit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import top.foxball.cartask.entity.AuditEvent

class AuditActionTests {
    @Test
    fun `动作编码唯一且关键动作标记高风险`() {
        val actions = AuditAction.entries
        assertEquals(actions.size, actions.map { it.code }.toSet().size)
        assertTrue(actions.any { it == AuditAction.SENSITIVE_DATA_EXPORTED && it.category == AuditEvent.Category.DATA_EXPORT })
        assertTrue(actions.any { it.riskLevel == AuditEvent.RiskLevel.CRITICAL })
    }
}
