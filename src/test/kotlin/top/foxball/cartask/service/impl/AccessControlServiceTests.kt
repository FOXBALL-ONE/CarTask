package top.foxball.cartask.service.impl

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.context.SecurityContextImpl
import top.foxball.cartask.authentication.CurrentUserPrincipal
import top.foxball.cartask.entity.AccessControl
import top.foxball.cartask.entity.Department
import top.foxball.cartask.entity.type.AccessControlType
import top.foxball.cartask.handler.BusinessException
import top.foxball.cartask.repository.AccessControlRepository
import top.foxball.cartask.repository.AccessControlTypeRepository
import top.foxball.cartask.repository.DepartmentRepository
import top.foxball.cartask.scope.DataScope
import top.foxball.cartask.scope.DataScopeResolver
import top.foxball.cartask.scope.ScopeGuard
import java.time.LocalDateTime
import java.util.Optional

class AccessControlServiceTests {
    private val repository = mock<AccessControlRepository>()
    private val departmentRepository = mock<DepartmentRepository>()
    private val permissionRepository = mock<AccessControlTypeRepository>()

    private val dataScopeResolver = mock<DataScopeResolver>()
    private val scopeGuard = ScopeGuard(dataScopeResolver, mock(), mock())
    private val service = AccessControlServiceImpl(
        repository, departmentRepository, permissionRepository, scopeGuard,
    )

    private fun allScope() = whenever(dataScopeResolver.current()).thenReturn(DataScope.All)

    private fun departmentScope(vararg ids: Long) = whenever(dataScopeResolver.current())
        .thenReturn(DataScope.departments(ids.toSet(), emptySet(), emptySet()))

    private fun department(id: Long) = Department().apply {
        this.id = id
        name = "部门$id"
        departmentNumber = "D$id"
    }

    private fun permission(id: Long) = AccessControlType().apply {
        this.id = id
        accessControlName = "类型$id"
    }

    
    private fun authenticate() {
        val principal = CurrentUserPrincipal(
            userId = 692, username = "t_dept_d1", role = "DEPT_ADMIN", tokenId = "test-token",
        )
        SecurityContextHolder.setContext(
            SecurityContextImpl(UsernamePasswordAuthenticationToken(principal, null, principal.authorities)),
        )
    }

    @AfterEach
    fun clearAuthentication() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `创建门禁授权强制进入待审核且未同步`() {
        allScope()
        val entity = AccessControl().apply {
            name = "张三"
            reviewStatus = AccessControl.ReviewStatus.APPROVED
            synchronizedLoading = true
        }
        whenever(repository.save(entity)).thenReturn(entity)

        service.create(entity)

        assertEquals(AccessControl.ReviewStatus.PENDING, entity.reviewStatus)
        assertFalse(entity.synchronizedLoading)
    }

    @Test
    fun `已审核申请修改后回到待审核`() {
        allScope()
        val current = AccessControl().apply {
            id = 7
            name = "旧名称"
            reviewStatus = AccessControl.ReviewStatus.APPROVED
            synchronizedLoading = true
        }
        val incoming = AccessControl().apply {
            id = 7
            name = "新名称"
        }
        whenever(repository.findById(7)).thenReturn(Optional.of(current))
        whenever(repository.save(current)).thenReturn(current)

        service.update(7, incoming)

        assertEquals(AccessControl.ReviewStatus.PENDING, current.reviewStatus)
        assertFalse(current.synchronizedLoading)
    }

    @Test
    fun `设备同步未接入时返回 501 而不是 500`() {
        allScope()
        val current = AccessControl().apply {
            id = 7
            name = "张三"
            reviewStatus = AccessControl.ReviewStatus.APPROVED
        }
        whenever(repository.findById(7)).thenReturn(Optional.of(current))

        val ex = assertThrows(BusinessException::class.java) { service.synchronize(7) }
        assertEquals(501, ex.code)
        assertFalse(current.synchronizedLoading)
    }

    @Test
    fun `授权结束时间必须晚于开始时间`() {
        allScope()
        val entity = AccessControl().apply {
            name = "张三"
            upTime = LocalDateTime.of(2026, 8, 20, 9, 0)
            endTime = upTime
        }

        assertThrows(IllegalArgumentException::class.java) { service.create(entity) }
    }

    @Test
    fun `门禁授权不允许物理删除`() {
        assertThrows(org.springframework.security.access.AccessDeniedException::class.java) { service.delete(7) }
        assertThrows(org.springframework.security.access.AccessDeniedException::class.java) { service.deleteBatch(listOf(7)) }
    }

    
    @Test
    fun `部门管理读不到也审核不了其他部门的授权`() {
        departmentScope(1L)
        val other = AccessControl().apply {
            id = 9
            name = "别部门的授权"
            department = department(2L)
            reviewStatus = AccessControl.ReviewStatus.PENDING
        }
        whenever(repository.findById(9)).thenReturn(Optional.of(other))

        val detail = assertThrows(IllegalArgumentException::class.java) { service.get(9) }
        assertEquals("记录不存在", detail.message)
        assertThrows(IllegalArgumentException::class.java) { service.review(9, true, "越权审核") }
        assertThrows(IllegalArgumentException::class.java) { service.update(9, AccessControl().apply { id = 9 }) }
    }

    @Test
    fun `部门管理读得到本部门的授权`() {
        departmentScope(1L)
        authenticate()
        val mine = AccessControl().apply {
            id = 8
            name = "本部门授权"
            department = department(1L)
            reviewStatus = AccessControl.ReviewStatus.PENDING
        }
        whenever(repository.findById(8)).thenReturn(Optional.of(mine))

        assertEquals(8L, service.get(8).id)

        whenever(repository.save(mine)).thenReturn(mine)
        service.review(8, true, "资料齐全")
        assertEquals(AccessControl.ReviewStatus.APPROVED, mine.reviewStatus)
    }

    @Test
    fun `受限范围下不能建无部门的授权`() {
        departmentScope(1L)
        val orphan = AccessControl().apply { name = "没有部门的授权" }

        val ex = assertThrows(IllegalArgumentException::class.java) { service.create(orphan) }
        assertEquals("必须指定部门", ex.message)
        verify(repository, never()).save(orphan)
    }

    @Test
    fun `受限范围下不能建到范围外的部门`() {
        departmentScope(1L)
        val foreign = AccessControl().apply {
            name = "别部门的授权"
            department = department(2L)
        }
        whenever(departmentRepository.findById(2L)).thenReturn(Optional.of(department(2L)))

        assertThrows(org.springframework.security.access.AccessDeniedException::class.java) {
            service.create(foreign)
        }
        verify(repository, never()).save(foreign)
    }




    @Test
    fun `创建时把请求体里只有 id 的部门换成受管实体`() {
        allScope()
        val managed = department(2L)
        whenever(departmentRepository.findById(2L)).thenReturn(Optional.of(managed))
        val incoming = AccessControl().apply {
            name = "张三"
            department = Department().apply { id = 2 }
        }
        whenever(repository.save(incoming)).thenReturn(incoming)

        service.create(incoming)

        assertEquals("部门2", incoming.department?.name)
        assertEquals("D2", incoming.department?.departmentNumber)
    }

    @Test
    fun `更新时不带部门则保持原部门`() {
        allScope()
        val managed = department(1L)
        val current = AccessControl().apply {
            id = 5
            name = "旧名称"
            department = managed
            reviewStatus = AccessControl.ReviewStatus.PENDING
        }
        whenever(repository.findById(5)).thenReturn(Optional.of(current))
        whenever(repository.save(current)).thenReturn(current)

        service.update(5, AccessControl().apply { id = 5; name = "新名称" })

        assertEquals(1L, current.department?.id)
    }





    @Test
    fun `更新时不带授权类型则保持原类型`() {
        allScope()
        val current = AccessControl().apply {
            id = 6
            name = "旧名称"
            department = department(1L)
            accessControlPermission = permission(3L)
            reviewStatus = AccessControl.ReviewStatus.PENDING
        }
        whenever(repository.findById(6)).thenReturn(Optional.of(current))
        whenever(repository.save(current)).thenReturn(current)

        service.update(6, AccessControl().apply { id = 6; name = "新名称" })

        assertEquals(3L, current.accessControlPermission?.id)
    }

    
    @Test
    fun `挂不存在的授权类型时报错而不是落到外键异常`() {
        allScope()
        whenever(departmentRepository.findById(1L)).thenReturn(Optional.of(department(1L)))
        whenever(permissionRepository.findById(999L)).thenReturn(Optional.empty())
        val entity = AccessControl().apply {
            name = "张三"
            department = department(1L)
            accessControlPermission = permission(999L)
        }

        val ex = assertThrows(IllegalArgumentException::class.java) { service.create(entity) }
        assertEquals("门禁授权类型不存在：999", ex.message)
        verify(repository, never()).save(entity)
    }




    @Test
    fun `人员编号重复时给出可读提示`() {
        allScope()
        whenever(departmentRepository.findById(1L)).thenReturn(Optional.of(department(1L)))
        whenever(repository.findByPersonNumber("AC-1")).thenReturn(
            AccessControl().apply { id = 99; name = "已存在的"; personNumber = "AC-1" },
        )
        val entity = AccessControl().apply {
            name = "张三"
            personNumber = "AC-1"
            department = department(1L)
        }

        val ex = assertThrows(IllegalArgumentException::class.java) { service.create(entity) }
        assertEquals("人员编号已存在", ex.message)
        verify(repository, never()).save(entity)
    }

    
    @Test
    fun `更新时自己的人员编号不算重复`() {
        allScope()
        val current = AccessControl().apply {
            id = 6
            name = "旧名称"
            personNumber = "AC-6"
            department = department(1L)
            reviewStatus = AccessControl.ReviewStatus.PENDING
        }
        whenever(repository.findById(6)).thenReturn(Optional.of(current))
        whenever(repository.findByPersonNumber("AC-6")).thenReturn(current)
        whenever(repository.save(current)).thenReturn(current)

        service.update(6, AccessControl().apply { id = 6; name = "新名称" })

        assertEquals("新名称", current.name)
    }
}
