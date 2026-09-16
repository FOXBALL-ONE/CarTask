package top.foxball.cartask.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SerialNumbersTests {
    @Test
    fun `编号补零到四位`() {
        assertEquals("0001", SerialNumbers.format(1))
        assertEquals("0042", SerialNumbers.format(42))
        assertEquals("1234", SerialNumbers.format(1234))
    }

    @Test
    fun `超过四位的编号原样展示`() {
        assertEquals("12345", SerialNumbers.format(12345))
    }

    @Test
    fun `补零前后两种写法都能搜到同一条记录`() {
        assertTrue(SerialNumbers.matches(1, "1"))
        assertTrue(SerialNumbers.matches(1, "0001"))
        assertTrue(SerialNumbers.matches(42, "0042"))
    }

    @Test
    fun `编号按包含匹配`() {
        assertTrue(SerialNumbers.matches(1200, "12"))
        assertFalse(SerialNumbers.matches(3400, "12"))
    }

    @Test
    fun `没有编号的记录不参与编号匹配`() {
        assertFalse(SerialNumbers.matches(null, "1"))
    }
}
