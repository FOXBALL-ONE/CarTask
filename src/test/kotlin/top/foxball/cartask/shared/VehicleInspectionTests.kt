package top.foxball.cartask.shared

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VehicleInspectionTests {
    private val today = LocalDate.parse("2026-09-12")

    @Test
    fun `未登记年检日期与有效期的车辆是未年检`() {
        assertFalse(VehicleInspection.inspected(null, null))
        assertEquals("未年检", VehicleInspection.status(null, null, today))
    }

    @Test
    fun `有效期早于今天的车辆是已过期`() {
        assertEquals("已过期", VehicleInspection.status(LocalDate.parse("2025-05-20"), LocalDate.parse("2026-05-20"), today))
        // 有效期当天仍算有效：到期日当天的车不该显示为过期。
        assertEquals("有效", VehicleInspection.status(null, today, today))
    }

    @Test
    fun `只登记年检日期时按有效处理并默认顺延一年`() {
        assertEquals("有效", VehicleInspection.status(LocalDate.parse("2026-05-20"), null, today))
        assertEquals(LocalDate.parse("2027-05-20"), VehicleInspection.defaultValidUntil(LocalDate.parse("2026-05-20")))
    }

    @Test
    fun `年检日期或有效期任一存在即视为已年检`() {
        assertTrue(VehicleInspection.inspected(LocalDate.parse("2026-05-20"), null))
        assertTrue(VehicleInspection.inspected(null, LocalDate.parse("2027-05-20")))
    }
}
