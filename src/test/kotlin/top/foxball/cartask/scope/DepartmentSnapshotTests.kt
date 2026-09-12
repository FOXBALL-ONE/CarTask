package top.foxball.cartask.scope

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import top.foxball.cartask.entity.Department

class DepartmentSnapshotTests {
    private fun department(id: Long, name: String, code: String, superior: Department? = null) =
        Department().apply {
            this.id = id
            this.name = name
            departmentNumber = code
            this.superior = superior
        }

    private val root = department(1, "运营中心", "OPS")
    private val parking = department(2, "停车管理组", "PARKING", root)
    private val gate = department(3, "门禁安全组", "SECURITY", root)
    private val snapshot = DepartmentSnapshot(listOf(root, parking, gate))

    @Test
    fun `按编码或名称精确解析部门`() {
        assertEquals("PARKING", snapshot.toCode("PARKING"))
        assertEquals("PARKING", snapshot.toCode("  停车管理组  "))
        assertEquals("PARKING", snapshot.toCode("停车管理组"))
    }

    @Test
    fun `不做模糊匹配`() {
        // 模糊匹配会把别的部门的数据匹配进来，那是数据泄露，因此只接受精确相等。
        assertNull(snapshot.toCode("停车管理"))
        assertNull(snapshot.toCode("停车管理组东区"))
        assertNull(snapshot.toCode("PARK"))
    }

    @Test
    fun `空值与空白解析为空`() {
        assertNull(snapshot.toCode(null))
        assertNull(snapshot.toCode(""))
        assertNull(snapshot.toCode("   "))
    }

    @Test
    fun `解析不出的部门返回空而不是兜底`() {
        assertNull(snapshot.toCode("不存在的部门"))
    }

    @Test
    fun `展开下级部门包含自身与全部后代`() {
        assertEquals(setOf(1L, 2L, 3L), snapshot.withDescendants(setOf(1L)))
        assertEquals(setOf(2L), snapshot.withDescendants(setOf(2L)))
        assertEquals(emptySet<Long>(), snapshot.withDescendants(emptySet()))
    }

    @Test
    fun `部门集合映射为编码与名称`() {
        assertEquals(setOf("OPS", "PARKING"), snapshot.codesOf(setOf(1L, 2L)))
        assertEquals(setOf("运营中心", "停车管理组"), snapshot.namesOf(setOf(1L, 2L)))
    }

    @Test
    fun `同名部门只保留一个编码避免歧义`() {
        val duplicated = DepartmentSnapshot(
            listOf(department(1, "同名部门", "A"), department(2, "同名部门", "B")),
        )

        // 名称解析本就不确定，结果必须稳定落在其中一个而不是随机；这类数据应由回填接口按编码修正。
        assertTrue(duplicated.toCode("同名部门") in setOf("A", "B"))
        assertEquals("A", duplicated.toCode("A"))
        assertEquals("B", duplicated.toCode("B"))
    }
}
