package top.foxball.cartask.scope

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.security.access.AccessDeniedException
import top.foxball.cartask.entity.ParkingOwner

class ScopeGuardTests {
    private val dataScopeResolver = mock<DataScopeResolver>()
    private val departmentLinkResolver = mock<DepartmentLinkResolver>()
    private val scopeQuerySupport = mock<ScopeQuerySupport>()
    private val guard = ScopeGuard(dataScopeResolver, departmentLinkResolver, scopeQuerySupport)

    private val departmentScope = DataScope.departments(
        ids = setOf(1L, 2L),
        codes = setOf("OPS", "PARKING"),
        names = setOf("运营中心", "停车管理组"),
    )

    private fun owner(dept: String, departmentCode: String? = null) = ParkingOwner().apply {
        id = 1L
        cardId = "CARD-1"
        name = "张三"
        this.dept = dept
        this.departmentCode = departmentCode
        phone = "13800138000"
    }

    @Test
    fun `请求范围外的部门必须被拒绝而不是照它过滤`() {
        val filter = guard.requestedDepartment(99L, departmentScope)

        assertTrue(filter.denied)
    }

    @Test
    fun `请求范围内的部门被接受`() {
        val filter = guard.requestedDepartment(2L, departmentScope)

        assertFalse(filter.denied)
        assertEquals(2L, filter.departmentId)
    }

    @Test
    fun `不传部门参数时不额外过滤`() {
        val filter = guard.requestedDepartment(null, departmentScope)

        assertFalse(filter.denied)
        assertNull(filter.departmentId)
    }

    @Test
    fun `不限范围时客户端可自由指定部门`() {
        val filter = guard.requestedDepartment(99L, DataScope.All)

        assertFalse(filter.denied)
        assertEquals(99L, filter.departmentId)
    }

    @Test
    fun `写路径拒绝范围外的部门`() {
        assertThrows(AccessDeniedException::class.java) { guard.requireDepartmentAllowed(99L, departmentScope) }
        assertDoesNotThrow { guard.requireDepartmentAllowed(2L, departmentScope) }
        assertDoesNotThrow { guard.requireDepartmentAllowed(null, departmentScope) }
    }

    @Test
    fun `不限范围时任何部门都可写`() {
        assertDoesNotThrow { guard.requireDepartmentAllowed(99L, DataScope.All) }
    }

    @Test
    fun `详情按编码判定归属并拒绝范围外的行`() {
        assertDoesNotThrow { guard.requireVisible(owner("停车管理组", "PARKING"), departmentScope) }
        assertThrows(AccessDeniedException::class.java) {
            guard.requireVisible(owner("别的部门", "OTHER"), departmentScope)
        }
    }

    @Test
    fun `详情在没有编码时按名称兜底解析`() {
        whenever(departmentLinkResolver.snapshot()).thenReturn(DepartmentSnapshot(departmentRows()))

        assertDoesNotThrow { guard.requireVisible(owner("停车管理组"), departmentScope) }
    }

    @Test
    fun `解析不出部门归属的行一律拒绝`() {
        whenever(departmentLinkResolver.snapshot()).thenReturn(DepartmentSnapshot(emptyList()))

        assertThrows(AccessDeniedException::class.java) {
            guard.requireVisible(owner("查不到的部门"), departmentScope)
        }
    }

    @Test
    fun `不限范围时详情一律放行`() {
        assertDoesNotThrow { guard.requireVisible(owner("查不到的部门"), DataScope.All) }
    }

    @Test
    fun `本人范围下主数据详情一律拒绝`() {
        // 车主/车牌这类主数据页面没有普通用户入口，fail closed 比放开安全。
        assertThrows(AccessDeniedException::class.java) {
            guard.requireVisible(owner("停车管理组", "PARKING"), DataScope.self(7L, "13800138000", setOf("A1"), emptySet(), emptySet()))
        }
    }

    @Test
    fun `部门管理只能修改范围内账号`() {
        assertDoesNotThrow { guard.requireUserInScope(2L, departmentScope) }
        assertThrows(AccessDeniedException::class.java) { guard.requireUserInScope(99L, departmentScope) }
    }

    @Test
    fun `受限范围下没有部门归属的账号也拒绝修改`() {
        // 范围解析不出归属时的语义是「看不到」，用户账号同样不能例外。
        assertThrows(AccessDeniedException::class.java) { guard.requireUserInScope(null, departmentScope) }
        assertThrows(AccessDeniedException::class.java) {
            guard.requireUserInScope(1L, DataScope.self(7L, "13800138000", setOf("A1"), emptySet(), emptySet()))
        }
    }

    @Test
    fun `不限范围时可以修改任意账号`() {
        assertDoesNotThrow { guard.requireUserInScope(99L, DataScope.All) }
        assertDoesNotThrow { guard.requireUserInScope(null, DataScope.All) }
    }

    private fun departmentRows() = listOf(
        top.foxball.cartask.entity.Department().apply {
            id = 2L
            name = "停车管理组"
            departmentNumber = "PARKING"
        },
    )
}
